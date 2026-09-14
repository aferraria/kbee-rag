package kbee.rag.search;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.common.params.ModifiableSolrParams;
import org.springframework.stereotype.Component;

import kbee.rag.embedding.EmbeddingService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component("vectorSegmentSearcher")
public class VectorSegmentSearcher
        implements SegmentSearcher {

    private final EmbeddingService embeddingService;

    private final SegmentDao solrService;

    public VectorSegmentSearcher(
            EmbeddingService embeddingService,
            SegmentDao solrService) {

        this.embeddingService =
                embeddingService;

        this.solrService =
                solrService;
    }

    @Override
    public Flux<SegmentSearchResult> search(
            SegmentSearchRequest request) {

        ExtendedSegmentSearchRequest extended =
                (ExtendedSegmentSearchRequest) request;

        Mono<List<Float>> originalEmbedding =
                embeddingService.embedReactive(
                        extended.query()
                );

        Mono<List<Float>> extendedEmbedding =
                embeddingService.embedReactive(
                        extended.extendedQuery()
                );

        return Mono.zip(
                originalEmbedding,
                extendedEmbedding
        )
        .flatMapMany(tuple -> {

            List<Float> originalVector =
                    tuple.getT1();

            List<Float> extendedVector =
                    tuple.getT2();

            Flux<SegmentSearchResult> originalResults =
                    searchVector(
                            "embedding",
                            originalVector,
                            request.filters(),
                            extended.topK()
                    );

            Flux<SegmentSearchResult> extendedResults =
                    searchVector(
                            "legal_embedding",
                            extendedVector,
                            request.filters(),
                            extended.topK()
                    );

            return Mono.zip(
                    originalResults
                            .collectList()
                            .doOnNext(results -> {

                                System.out.println(
                                        "===== ORIGINAL ====="
                                );

                                printResults(results);
                            }),

                    extendedResults
                            .collectList()
                            .doOnNext(results -> {

                                System.out.println(
                                        "===== EXTENDED ====="
                                );

                                printResults(results);
                            })
            )
            .flatMapMany(results ->

                    Flux.fromIterable(
                            merge(
                                    results.getT1(),
                                    results.getT2()
                            )
                    )
            );
        });
    }
    
    private Flux<SegmentSearchResult> searchVector(
            String field,
            List<Float> embedding,
            List<String> filters,
            int topK) {

        Flux<SegmentSearchResult> decisions =
                searchVector(
                        field,
                        embedding,
                        filters,
                        "document_type:fallo"
                        + " AND "
                        + "-section_id:RESUELVE",
                        topK
                );

        Flux<SegmentSearchResult> summaries =
                searchVector(
                        field,
                        embedding,
                        filters,
                        "document_type:sumario",
                        topK
                );

        return Flux.concat(
                decisions,
                summaries
        );
    }

    private Flux<SegmentSearchResult> searchVector(
            String field,
            List<Float> embedding,
            List<String> filters,
            String filter,
            int topK) {

        ModifiableSolrParams params =
                new ModifiableSolrParams();

        params.set(
                "q",
                "{!knn f="
                        + field
                        + " topK="
                        + topK
                        + "}"
                        + toVectorString(embedding)
        );

        params.add(
                "fq",
                filter
        );
        
        
        for (String f : filters) {
            params.add(
                    "fq",
                    f
            );
        }

        params.set(
                "rows",
                topK
        );

        return solrService.search(params);
    }

    private String toVectorString(
            List<Float> vector) {

        StringBuilder sb =
                new StringBuilder("[");

        for (int i = 0;
                i < vector.size();
                i++) {

            if (i > 0) {
                sb.append(",");
            }

            sb.append(
                    vector.get(i)
            );
        }

        sb.append("]");

        return sb.toString();
    }

    private List<SegmentSearchResult> merge(
            List<SegmentSearchResult> original,
            List<SegmentSearchResult> extended) {

        Map<String, MergedResult> merged =
                new LinkedHashMap<>();

        addResults(
                merged,
                original,
                1.0
        );

        addResults(
                merged,
                extended,
                1.0
        );

        return merged.values()
                .stream()
                .sorted(
                        Comparator
                                .comparingDouble(
                                        (MergedResult r) ->
                                                r.score()
                                )
                                .reversed()
                )
                .map(
                        (MergedResult r) ->
                                withScore(
                                        r.result(),
                                        r.score()
                                )
                )
                .toList();
    }

    private void addResults(
            Map<String, MergedResult> merged,
            List<SegmentSearchResult> results,
            double weight) {

        final double k = 60.0;

        for (int i = 0;
                i < results.size();
                i++) {

            SegmentSearchResult result =
                    results.get(i);

            double score =
                    weight
                            / (k + i + 1);

            String key =
                    effectiveDocumentId(
                            result
                    );

            merged.compute(
                    key,
                    (id, existing) -> {

                        if (existing == null) {

                            return new MergedResult(
                                    result,
                                    score
                            );
                        }

                        SegmentSearchResult bestResult =
                                result.score()
                                        > existing.result().score()
                                                ? result
                                                : existing.result();

                        return new MergedResult(
                                bestResult,
                                existing.score()
                                        + score
                        );
                    }
            );
        }
    }
    
    private String effectiveDocumentId(
            SegmentSearchResult result) {

        String documentId =
                result.documentId();

        if (documentId == null
                || documentId.isBlank()) {

            return result.id();
        }

        if (documentId.startsWith(
                "sumario-fallo-"
        )) {

            int lastDash =
                    documentId.lastIndexOf('-');

            if (lastDash > 0) {

                return documentId.substring(
                        "sumario-".length(),
                        lastDash
                );
            }
        }

        return documentId;
    }
    
    private SegmentSearchResult withScore(
            SegmentSearchResult result,
            double score) {

        return new SegmentSearchResult(
                result.id(),
                result.documentId(),
                result.documentTitle(),
                result.documentDate(),
                result.sectionId(),
                result.sectionTitle(),
                result.sectionPath(),
                result.segmentNumber(),
                result.sectionSegmentNumber(),
                result.text(),
                (float) score
        );
    }
    
    private record MergedResult(
            SegmentSearchResult result,
            double score) {
    }
    
    private void printResults(
            List<SegmentSearchResult> results) {

        for (int i = 0;
                i < results.size();
                i++) {

            SegmentSearchResult result =
                    results.get(i);

            System.out.println(
                    (i + 1)
                            + "|"
                            + result.score()
                            + "|"
                            + result.id()
            );
        }
    }
}
package kbee.rag.search;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import kbee.rag.embedding.EmbeddingService;
import reactor.core.publisher.Flux;

@Service
public class SegmentSearchService {

    private final SegmentSearcher segmentSearcher;

    private final EmbeddingService embeddingService;

    private final SegmentDao solrService;

    public SegmentSearchService(
            @Qualifier("compositeSegmentSearcher")
            SegmentSearcher segmentSearcher,
            EmbeddingService embeddingService,
            SegmentDao solrService) {

        this.segmentSearcher =
                segmentSearcher;

        this.embeddingService =
                embeddingService;

        this.solrService =
                solrService;
    }

    public Flux<SegmentSearchResult> search(
            String question,
            List<String> filters,
            int topK) {

        return segmentSearcher.search(
                new SegmentSearchRequest(
                        question,
                        filters,
                        topK
                )
        );
    }

    public Flux<SegmentSearchResult> searchWithinDocument(
            String question,
            String documentId,
            int topK) {

        return embeddingService
                .embedReactive(question)
                .flatMapMany(embedding -> {

                    ModifiableSolrParams params =
                            new ModifiableSolrParams();

                    params.set(
                            "q",
                            "{!knn f=embedding topK="
                                    + topK
                                    + "}"
                                    + toVectorString(
                                            embedding
                                    )
                    );

                    params.add(
                            "fq",
                            "document_id:\""
                                    + ClientUtils.escapeQueryChars(
                                            documentId
                                    )
                                    + "\""
                    );

                    params.set(
                            "rows",
                            topK
                    );

                    return solrService.search(
                            params
                    );
                });
    }

    public Flux<SegmentSearchResult> findNeighbors(
            SegmentSearchResult source,
            int neighborDistance) {

        if (source == null
                || source.documentId() == null
                || source.segmentNumber() == null) {

            return Flux.empty();
        }

        int from =
                Math.max(
                        0,
                        source.segmentNumber()
                                - neighborDistance
                );

        int to =
                source.segmentNumber()
                        + neighborDistance;

        ModifiableSolrParams params =
                new ModifiableSolrParams();

        params.set(
                "q",
                "document_id:\""
                        + ClientUtils.escapeQueryChars(
                                source.documentId()
                        )
                        + "\""
        );

        params.add(
                "fq",
                "segment_number:["
                        + from
                        + " TO "
                        + to
                        + "]"
        );

        params.set(
                "rows",
                neighborDistance * 2 + 1
        );

        params.set(
                "sort",
                "segment_number asc"
        );

        return solrService.search(
                params
        );
    }

    private String toVectorString(
            List<Float> vector) {

        return vector.stream()
                .map(String::valueOf)
                .collect(
                        Collectors.joining(
                                ",",
                                "[",
                                "]"
                        )
                );
    }
}
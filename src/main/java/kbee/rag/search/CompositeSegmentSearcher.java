package kbee.rag.search;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component("compositeSegmentSearcher")
public class CompositeSegmentSearcher
        implements SegmentSearcher {

    private final SegmentQueryBuilder queryBuilder;

    private final SegmentSearcher vectorSegmentSearcher;

    private final SegmentSearcher lexicalSegmentSearcher;

    public CompositeSegmentSearcher(
            SegmentQueryBuilder queryBuilder,
            @Qualifier("vectorSegmentSearcher")
            SegmentSearcher vectorSearcher,
            @Qualifier("lexicalSegmentSearcher")
            SegmentSearcher lexicalSearcher) {

        this.queryBuilder =
                queryBuilder;

        this.vectorSegmentSearcher =
                vectorSearcher;

        this.lexicalSegmentSearcher =
                lexicalSearcher;
    }

    @Override
    public Flux<SegmentSearchResult> search(
            SegmentSearchRequest request) {

        return queryBuilder
                .build(request)
                .flatMapMany(extendedRequest -> {

                    Mono<List<SegmentSearchResult>> vectorResults =
                            vectorSegmentSearcher
                                    .search(extendedRequest)
                                    .collectList();

                    Mono<List<SegmentSearchResult>> lexicalResults =
                            lexicalSegmentSearcher
                                    .search(extendedRequest)
                                    .collectList();

                    return Mono.zip(
                            vectorResults,
                            lexicalResults
                    ).flatMapMany(tuple ->
                            Flux.fromIterable(
                                    merge(
                                            tuple.getT1(),
                                            tuple.getT2()
                                    )
                            )
                    );
                });
    }

    private List<SegmentSearchResult> merge(
            List<SegmentSearchResult> vectorResults,
            List<SegmentSearchResult> lexicalResults) {

        List<SegmentSearchResult> normalizedVector =
                normalize(
                        vectorResults
                );

        List<SegmentSearchResult> normalizedLexical =
                normalize(
                        lexicalResults
                );

        int i = 0;
        System.out.println("VEC");
        for (SegmentSearchResult result : normalizedVector) {
        	System.out.println(++i + "|" +result.score() + "|" + result.documentId());
        }
        i =0;
        System.out.println("LEX");
        for (SegmentSearchResult result : normalizedLexical) {
        	System.out.println(++i + "|" +result.score() + "|" + result.documentId());
        }

        
        Map<String, SegmentSearchResult> merged =
                new LinkedHashMap<>();

        for (SegmentSearchResult result
                : normalizedVector) {

            merged.merge(
                    result.id(),
                    result,
                    this::bestResult
            );
        }

        for (SegmentSearchResult result
                : normalizedLexical) {

            merged.merge(
                    result.id(),
                    result,
                    this::bestResult
            );
        }
        
        List<SegmentSearchResult> result = merged.values()
                .stream()
                .sorted(
                        Comparator.comparing(
                                SegmentSearchResult::score
                        ).reversed()
                )
                .toList();
        
        i = 1;
        System.out.println("MERGE");
        for (SegmentSearchResult s : result) {
        	System.out.println(i++ + "|"+ s.score() + "|" + s.documentId());
        }
        
        return result;
    }
    
    private List<SegmentSearchResult> normalize(
            List<SegmentSearchResult> results) {

        if (results == null
                || results.isEmpty()) {

            return List.of();
        }

        float maxScore =
                results.stream()
                        .map(SegmentSearchResult::score)
                        .filter(Objects::nonNull)
                        .max(Float::compare)
                        .orElse(1.0f);

        if (maxScore <= 0) {
            maxScore = 1.0f;
        }

        final float divisor =
                maxScore;

        return results.stream()
                .map(result ->
                        new SegmentSearchResult(
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
                                result.score() == null
                                        ? 0.0f
                                        : result.score()
                                                / divisor
                        )
                )
                .toList();
    }

    private SegmentSearchResult bestResult(
            SegmentSearchResult a,
            SegmentSearchResult b) {

        return a.score() >= b.score()
                ? a
                : b;
    }
}
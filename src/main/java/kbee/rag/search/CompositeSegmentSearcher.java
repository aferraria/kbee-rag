package kbee.rag.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component("compositeSegmentSearcher")
public class CompositeSegmentSearcher
        implements SegmentSearcher {

    private static final double RRF_K =
            60.0;

    private static final double VECTOR_WEIGHT =
            1.0;

    private static final double LEXICAL_WEIGHT =
            0.7;

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
                .build(
                        request
                )
                .flatMapMany(extendedRequest -> {

                    Mono<List<SegmentSearchResult>> vectorResults =
                            vectorSegmentSearcher
                                    .search(
                                            extendedRequest
                                    )
                                    .collectList();

                    Mono<List<SegmentSearchResult>> lexicalResults =
                            lexicalSegmentSearcher
                                    .search(
                                            extendedRequest
                                    )
                                    .collectList();

                    return Mono.zip(
                            vectorResults,
                            lexicalResults
                    )
                    .flatMapMany(tuple -> {

                        List<SegmentSearchResult> vector =
                                tuple.getT1();

                        List<SegmentSearchResult> lexical =
                                tuple.getT2();

                        printRanking(
                                "VECTOR",
                                vector
                        );

                        printRanking(
                                "LEXICAL",
                                lexical
                        );

                        List<SegmentSearchResult> merged =
                                merge(
                                        vector,
                                        lexical
                                );

                        printRanking(
                                "MERGE",
                                merged
                        );

                        return Flux.fromIterable(
                                merged
                        );
                    });
                });
    }

    /*
     * =================================================
     * MERGE
     * =================================================
     */
    
    private static final int GUARANTEED_PER_CHANNEL = 5;



    private List<SegmentSearchResult> merge(
            List<SegmentSearchResult> vectorResults,
            List<SegmentSearchResult> lexicalResults) {

        Map<String, FusedResult> fused =
                new LinkedHashMap<>();

        addRanking(
                fused,
                vectorResults,
                VECTOR_WEIGHT
        );

        addRanking(
                fused,
                lexicalResults,
                LEXICAL_WEIGHT
        );

        List<SegmentSearchResult> rrf =
                fused.values()
                        .stream()
                        .sorted(
                                Comparator.comparingDouble(
                                        FusedResult::score
                                ).reversed()
                        )
                        .map(this::toSegmentSearchResult)
                        .toList();

        /*
         * Mapa de resultados con el score RRF.
         *
         * Es importante usar estos objetos también
         * para los resultados garantizados.
         */
        Map<String, SegmentSearchResult> rrfById =
                rrf.stream()
                        .collect(
                                Collectors.toMap(
                                        SegmentSearchResult::id,
                                        Function.identity(),
                                        (a, b) -> a,
                                        LinkedHashMap::new
                                )
                        );

        Map<String, SegmentSearchResult> result =
                new LinkedHashMap<>();

        /*
         * Garantizamos los mejores resultados lexicales,
         * pero conservando SIEMPRE el score RRF.
         */
        lexicalResults.stream()
                .limit(GUARANTEED_PER_CHANNEL)
                .map(r ->
                        rrfById.get(r.id())
                )
                .filter(Objects::nonNull)
                .forEach(r ->
                        result.putIfAbsent(
                                r.id(),
                                r
                        )
                );

        /*
         * Garantizamos los mejores resultados vectoriales,
         * también con su score RRF.
         */
        vectorResults.stream()
                .limit(GUARANTEED_PER_CHANNEL)
                .map(r ->
                        rrfById.get(r.id())
                )
                .filter(Objects::nonNull)
                .forEach(r ->
                        result.putIfAbsent(
                                r.id(),
                                r
                        )
                );

        /*
         * Completamos con el ranking RRF.
         */
        rrf.forEach(r ->
                result.putIfAbsent(
                        r.id(),
                        r
                )
        );

        return new ArrayList<>(
                result.values()
        );
    }
    /*
     * =================================================
     * ADD RANKING
     * =================================================
     */

    private void addRanking(
            Map<String, FusedResult> fused,
            List<SegmentSearchResult> results,
            double weight) {

        if (results == null
                || results.isEmpty()) {

            return;
        }

        for (int i = 0;
                i < results.size();
                i++) {

            SegmentSearchResult result =
                    results.get(i);

            if (result == null) {
                continue;
            }

            int rank =
                    i + 1;

            double rrfScore =
                    weight
                            / (
                                    RRF_K
                                            + rank
                            );

            fused.compute(
                    result.id(),
                    (id, existing) -> {

                        if (existing == null) {

                            return new FusedResult(
                                    result,
                                    rrfScore
                            );
                        }

                        return new FusedResult(
                                existing.result(),
                                existing.score()
                                        + rrfScore
                        );
                    }
            );
        }
    }

    /*
     * =================================================
     * TO RESULT
     * =================================================
     */

    private SegmentSearchResult toSegmentSearchResult(
            FusedResult fused) {

        SegmentSearchResult result =
                fused.result();

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
                (float) fused.score()
        );
    }

    /*
     * =================================================
     * DEBUG
     * =================================================
     */

    private void printRanking(
            String name,
            List<SegmentSearchResult> results) {

        System.out.println(
                "===== " + name + " ====="
        );

        if (results == null
                || results.isEmpty()) {

            System.out.println(
                    "(sin resultados)"
            );

            return;
        }

        int i = 1;

        for (SegmentSearchResult result :
                results) {

            System.out.println(
                    i++
                            + "|"
                            + result.score()
                            + "|"
                            + result.documentId()
                            + "|"
                            + result.id()
            );
        }
    }

    /*
     * =================================================
     * FUSED RESULT
     * =================================================
     */

    private record FusedResult(
            SegmentSearchResult result,
            double score) {
    }
}
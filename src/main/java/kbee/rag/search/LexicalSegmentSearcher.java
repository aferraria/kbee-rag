package kbee.rag.search;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.solr.common.params.ModifiableSolrParams;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

@Component("lexicalSegmentSearcher")
public class LexicalSegmentSearcher
        implements SegmentSearcher {

    private static final Pattern LAW_PATTERN =
            Pattern.compile(
                    "(?i)\\bley\\s+(?:n[°º]?\\s*)?(\\d+)\\b"
            );

    private static final double LAW_PHRASE_BOOST =
            30.0;

    private static final double LAW_NUMBER_BOOST =
            10.0;

    private final SegmentDao solrService;

    public LexicalSegmentSearcher(
            SegmentDao solrService) {

        this.solrService =
                solrService;
    }

    @Override
    public Flux<SegmentSearchResult> search(
            SegmentSearchRequest request) {

        ExtendedSegmentSearchRequest extended =
                (ExtendedSegmentSearchRequest) request;

        /*
         * Lexical legal:
         *
         * query original contra legal_proposition,
         * restringida por las voces seleccionadas.
         */
        Flux<SegmentSearchResult> legal =
                searchLegal(
                        extended.extendedQuery(),
                        extended.thesaurusTerms(),
                        extended.filters(),
                        extended.topK()
                );

        /*
         * Lexical original:
         *
         * query original contra segment_text,
         * sin filtro conceptual.
         */
        Flux<SegmentSearchResult> original =
                searchOriginal(
                        extended.extendedQuery(),
                        extended.filters(),
                        extended.topK()
                );

        return Flux.concat(
                legal,
                original
        );
    }

    /*
     * =================================================
     * LEXICAL LEGAL
     * =================================================
     */

    private Flux<SegmentSearchResult> searchLegal(
            String query,
            List<Concept> terms,
            List<String> filters,
            int topK) {

        if (query == null
                || query.isBlank()) {

            return Flux.empty();
        }

        ModifiableSolrParams params =
                new ModifiableSolrParams();

        params.set(
                "defType",
                "edismax"
        );

        /*
         * La query original se compara contra
         * las proposiciones jurídicas.
         */
        params.set(
                "q",
                query
        );

        params.set(
                "qf",
                "legal_proposition^1"
        );

        params.set(
                "pf",
                "legal_proposition^10"
        );

        params.set(
                "pf2",
                "legal_proposition^4"
        );

        params.set(
                "pf3",
                "legal_proposition^7"
        );

        params.set(
                "mm",
                "10%"
        );

        params.set(
                "q.op",
                "OR"
        );

        /*
         * Filtro general.
         */
        params.add(
                "fq",
                "(document_type:sumario OR document_id:fallo-*)"
        );
        
        for (String filter : filters) {
            params.add(
                    "fq",
                    filter
            );
        }

        /*
         * Al menos una de las voces seleccionadas
         * debe estar presente.
         */
        String conceptualFilter =
                buildConceptualFilter(
                        terms
                );

        if (!conceptualFilter.isBlank()) {

            params.add(
                    "fq",
                    conceptualFilter
            );
        }

        params.set(
                "rows",
                topK
        );

        return solrService.search(
                params
        );
    }

    /*
     * =================================================
     * CONCEPTUAL FILTER
     * =================================================
     */

    private String buildConceptualFilter(
            List<Concept> terms) {

        if (terms == null
                || terms.isEmpty()) {

            return "";
        }

        String values =
                terms.stream()
                        .filter(Objects::nonNull)
                        .filter(concept ->
                                concept.term() != null
                                        && !concept.term().isBlank()
                        )
                        .map(concept ->
                                "\""
                                        + escape(concept.term())
                                        + "\""
                        )
                        .collect(
                                Collectors.joining(
                                        " OR "
                                )
                        );

        if (values.isBlank()) {

            return "";
        }

        return "thesaurus_term:("
                + values
                + ")";
    }

    /*
     * =================================================
     * LEXICAL ORIGINAL
     * =================================================
     */

    private Flux<SegmentSearchResult> searchOriginal(
            String query,
            List<String> filters,
            int topK) {

        if (query == null
                || query.isBlank()) {

            return Flux.empty();
        }

        String lexicalQuery =
                buildOriginalQuery(
                        query
                );

        ModifiableSolrParams params =
                new ModifiableSolrParams();

        params.set(
                "defType",
                "edismax"
        );

        params.set(
                "q",
                lexicalQuery
        );

        params.set(
                "qf",
                "segment_text^1"
        );

        params.set(
                "pf",
                "segment_text^10"
        );

        params.set(
                "pf2",
                "segment_text^4"
        );

        params.set(
                "pf3",
                "segment_text^7"
        );

        params.set(
                "mm",
                "60%"
        );

        params.set(
                "q.op",
                "OR"
        );

        params.set(
                "fq",
                "(document_type:sumario OR document_id:fallo-*)"
        );
        
        for (String filter : filters) {
            params.add(
                    "fq",
                    filter
            );
        }

        params.set(
                "rows",
                topK
        );

        return solrService.search(
                params
        );
    }

    /*
     * =================================================
     * ORIGINAL QUERY
     * =================================================
     */

    private String buildOriginalQuery(
            String query) {

        List<String> lawNumbers =
                LAW_PATTERN
                        .matcher(query)
                        .results()
                        .map(match ->
                                match.group(1)
                        )
                        .distinct()
                        .toList();

        if (lawNumbers.isEmpty()) {

            return query;
        }

        String boostedLaws =
                lawNumbers.stream()
                        .map(number ->
                                "\"LEY "
                                        + number
                                        + "\"^"
                                        + LAW_PHRASE_BOOST
                                        + " OR "
                                        + number
                                        + "^"
                                        + LAW_NUMBER_BOOST
                        )
                        .collect(
                                Collectors.joining(
                                        " OR "
                                )
                        );

        return boostedLaws
                + " OR "
                + "\""
                + escape(query)
                + "\"";
    }

    /*
     * =================================================
     * ESCAPE
     * =================================================
     */

    private String escape(
            String value) {

        return value.replace(
                "\"",
                "\\\""
        );
    }
}
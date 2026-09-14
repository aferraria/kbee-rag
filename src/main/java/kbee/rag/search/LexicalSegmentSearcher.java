package kbee.rag.search;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.solr.client.solrj.util.ClientUtils;
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
                        extended.propositions(),
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
                        extended.query(),
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
            List<String> propositions,
            List<Concept> terms,
            List<String> filters,
            int topK) {

        if ((propositions == null || propositions.isEmpty())
                && (terms == null || terms.isEmpty())) {

            return Flux.empty();
        }

        ModifiableSolrParams params =
                new ModifiableSolrParams();

        String conceptualQuery =
                buildConceptualQuery(
                        terms
                );

        String propositionQuery =
                buildPropositionQuery(
                        propositions
                );

        String legalQuery;

        if (conceptualQuery.isBlank()) {

            legalQuery =
                    propositionQuery;

        } else if (propositionQuery.isBlank()) {

            legalQuery =
                    conceptualQuery;

        } else {

            legalQuery =
                    "("
                            + conceptualQuery
                            + ") OR ("
                            + propositionQuery
                            + ")";
        }

        params.set(
                "q",
                legalQuery
        );

        params.set(
                "q.op",
                "OR"
        );

        params.add(
                "fq",
                "(document_type:sumario OR document_type:fallo)"
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
    
    private String buildPropositionQuery(
            List<String> propositions) {

        if (propositions == null
                || propositions.isEmpty()) {

            return "";
        }

        return propositions.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .map(text ->
                        "legal_proposition:("
                                + ClientUtils.escapeQueryChars(text)
                                + ")^4"
                )
                .collect(
                        Collectors.joining(" OR ")
                );
    }
    
    private String buildConceptualQuery(
            List<Concept> terms) {

        if (terms == null || terms.isEmpty()) {
            return "";
        }

        return terms.stream()
                .filter(Objects::nonNull)
                .map(Concept::term)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(term -> !term.isBlank())
                .map(term -> {

                    int depth =
                            getConceptDepth(
                                    term
                            );

                    return "thesaurus_term:\""
                            + escape(term)
                            + "\"^"
                            + depth;
                })
                .collect(
                        Collectors.joining(
                                " OR "
                        )
                );
    }
    
    private int getConceptDepth(
            String term) {

        return (int) Arrays.stream(
                        term.split(">")
                )
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .count();
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
            return escape(query);
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
                + escape(query);
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
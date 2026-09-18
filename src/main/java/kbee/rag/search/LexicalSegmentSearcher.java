package kbee.rag.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
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
            8.0;

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
                		extended.query(),
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
            String question,
            List<String> propositions,
            List<Concept> terms,
            List<String> filters,
            int topK) {

        if ((propositions == null || propositions.isEmpty())
                && (terms == null || terms.isEmpty())
                && (question == null || question.isBlank())) {

            return Flux.empty();
        }

        ModifiableSolrParams params =
                new ModifiableSolrParams();

        /*
         * Detectamos si la consulta contiene una referencia
         * explícita a una ley.
         *
         * Ejemplo:
         *
         * "interpretación de la ley 12183"
         *
         * -> ["12183"]
         */
        List<String> lawNumbers =
                extractLawNumbers(
                        question
                );

        boolean hasLawNumbers =
                !lawNumbers.isEmpty();

        String conceptualQuery =
                buildConceptualQuery(
                        terms
                );

        /*
         * Sin número de ley:
         *
         * legal_proposition ^4
         *
         * Con número de ley:
         *
         * legal_proposition ^1
         *
         * porque el número de ley pasa a ser una señal
         * específica adicional.
         */
        String propositionQuery =
                buildPropositionQuery(
                        propositions,
                        hasLawNumbers
                                ? 1.0
                                : 4.0
                );

        /*
         * Si existe una referencia a una ley:
         *
         * Ley 12183
         *
         * agregamos:
         *
         * segment_text:(12183)^4
         */
        String lawNumberQuery =
                buildLawNumberQuery(
                        question
                );

        List<String> queryParts =
                new ArrayList<>();

        if (!conceptualQuery.isBlank()) {

            queryParts.add(
                    "("
                            + conceptualQuery
                            + ")"
            );
        }

        if (!propositionQuery.isBlank()) {

            queryParts.add(
                    "("
                            + propositionQuery
                            + ")"
            );
        }

        if (!lawNumberQuery.isBlank()) {

            queryParts.add(
                    "("
                            + lawNumberQuery
                            + ")"
            );
        }

        if (queryParts.isEmpty()) {
            return Flux.empty();
        }

        String legalQuery =
                String.join(
                        " OR ",
                        queryParts
                );

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

        if (filters != null) {

            for (String filter : filters) {

                if (filter != null
                        && !filter.isBlank()) {

                    params.add(
                            "fq",
                            filter
                    );
                }
            }
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
            List<String> propositions,
            double boost) {

        if (propositions == null
                || propositions.isEmpty()) {

            return "";
        }

        return propositions.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(proposition ->
                        !proposition.isBlank()
                )
                .map(proposition ->
                        "legal_proposition:("
                                + escape(proposition)
                                + ")^"
                                + boost
                )
                .collect(
                        Collectors.joining(
                                " OR "
                        )
                );
    }
    
//    private String buildConceptualQuery(
//            List<Concept> terms) {
//
//        if (terms == null || terms.isEmpty()) {
//            return "";
//        }
//
//        return terms.stream()
//                .filter(Objects::nonNull)
//                .map(Concept::term)
//                .filter(Objects::nonNull)
//                .map(String::trim)
//                .filter(term -> !term.isBlank())
//                .map(term -> {
//
//                    String[] components =
//                            term.split("\\s*>\\s*");
//
//                    int depth =
//                            components.length;
//
//                    if (depth == 1) {
//                        return "thesaurus_term:(\""
//                                + escape(term)
//                                + "\")^"
//                                + depth;
//                    }
//
//                    String componentQuery =
//                            Arrays.stream(components)
//                                    .map(String::trim)
//                                    .filter(s -> !s.isBlank())
//                                    .map(s ->
//                                            "\"" + escape(s) + "\""
//                                    )
//                                    .collect(
//                                            Collectors.joining(" AND ")
//                                    );
//
//                    return "thesaurus_term:("
//                            + componentQuery
//                            + ")^"
//                            + depth;
//                })
//                .collect(
//                        Collectors.joining(" OR ")
//                );
//    }
    
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
    
    private static final Pattern LAW_NUMBER_PATTERN =
            Pattern.compile(
                    "\\bley(?:es)?\\s+(\\d+(?:[./-]\\d+)*)",
                    Pattern.CASE_INSENSITIVE
                            | Pattern.UNICODE_CASE
            );
    
    private String buildLawNumberQuery(
            String question) {

        List<String> lawNumbers =
                extractLawNumbers(
                        question
                );

        if (lawNumbers.isEmpty()) {
            return "";
        }

        return lawNumbers.stream()
                .map(number ->
                        "segment_text:("
                                + ClientUtils.escapeQueryChars(
                                        number
                                )
                                + ")^"
                                + LAW_NUMBER_BOOST
                )
                .collect(
                        Collectors.joining(
                                " OR "
                        )
                );
    }
    
    private List<String> extractLawNumbers(
            String query) {

        if (query == null
                || query.isBlank()) {

            return List.of();
        }

        return LAW_PATTERN
                .matcher(query)
                .results()
                .map(match ->
                        match.group(1)
                )
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(number ->
                        !number.isBlank()
                )
                .distinct()
                .toList();
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
                extractLawNumbers(
                        query
                );

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
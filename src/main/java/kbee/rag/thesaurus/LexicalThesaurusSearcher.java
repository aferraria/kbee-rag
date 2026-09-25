package kbee.rag.thesaurus;

import java.util.ArrayList;
import java.util.List;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class LexicalThesaurusSearcher
        implements ThesaurusSearcher {

    private final SolrClient solrClient;

    private final String thesaurusCore;
    
    private final String thesaurusField;

    private final int topK;

    public LexicalThesaurusSearcher(
            SolrClient solrClient,
            @Value("${solr.target.core}")
            String thesaurusCore,
            @Value("${thesaurus.lexical.field:thesaurus_es_term}")
            String thesaurusField,
            @Value("${thesaurus.lexical.top-k:10}")
            int topK) {

        this.solrClient = solrClient;
        this.thesaurusField = thesaurusField;
        this.thesaurusCore = thesaurusCore;
        this.topK = topK;
    }

    @Override
    public Mono<List<Concept>> findCandidates(
            String text) {

        if (text == null || text.isBlank()) {
            return Mono.just(List.of());
        }

        return Mono.fromCallable(
                        () -> search(text)
                )
                .subscribeOn(
                        Schedulers.boundedElastic()
                );
    }

    private List<Concept> search(
            String text) throws Exception {

        SolrQuery query =
                new SolrQuery();

        query.setQuery(
                buildQuery(text)
        );

        query.set(
                "df",
                thesaurusField
        );

        query.set(
                "q.op",
                "OR"
        );

        query.addFilterQuery(
                "document_type:thesaurus"
        );

        query.setRows(
                topK
        );

        query.setFields(
                "id",
                "thesaurus_term",
                "score"
        );

        QueryResponse response;

        long start = System.nanoTime();

        try {

            response =
                    solrClient.query(
                            thesaurusCore,
                            query
                    );

        } finally {

//            long elapsedMs =
//                    (System.nanoTime() - start)
//                            / 1_000_000;
//
//            System.out.println(
//                    "LEXICAL SOLR PERF | elapsedMs="
//                            + elapsedMs
//                            + " | text="
//                            + text
//            );
        }

        List<Concept> result =
                new ArrayList<>();

        for (SolrDocument document :
                response.getResults()) {

            String term =
                    stringValue(
                            document,
                            "thesaurus_term"
                    );

            if (term == null
                    || term.isBlank()) {

                continue;
            }

            float score =
                    scoreValue(document);

            result.add(
                    new Concept(
                            term,
                            score
                    )
            );
        }

        return normalize(result)
                .stream()
                .filter(concept ->
                        concept.score() >= 0.80
                )
                .toList();
       }

    private List<Concept> normalize(
            List<Concept> candidates) {

        if (candidates.isEmpty()) {
            return List.of();
        }

        double maxScore =
                candidates.stream()
                        .mapToDouble(Concept::score)
                        .max()
                        .orElse(1.0);

        if (maxScore <= 0.0) {
            return List.copyOf(candidates);
        }

        return candidates.stream()
                .map(candidate ->
                        new Concept(
                                candidate.term(),
                                (float) (
                                        candidate.score()
                                                / maxScore
                                )
                        )
                )
                .toList();
    }
    private String buildQuery(
            String text) {

        String[] terms =
                text.trim()
                        .split("\\s+");

        List<String> clauses =
                new ArrayList<>();

        for (String term : terms) {

            if (term.isBlank()) {
                continue;
            }

            clauses.add(
                    escapeTerm(term)
            );
        }

        if (clauses.isEmpty()) {
            return "*:*";
        }

        return String.join(
                " OR ",
                clauses
        );
    }

    private String escapeTerm(
            String term) {

        return org.apache.solr.client.solrj.util.ClientUtils
                .escapeQueryChars(term);
    }

    private String stringValue(
            SolrDocument document,
            String field) {

        Object value =
                document.getFieldValue(
                        field
                );

        if (value == null) {
            return null;
        }

        if (value instanceof Iterable<?> values) {

            for (Object item : values) {

                if (item != null) {
                    return item.toString();
                }
            }

            return null;
        }

        return value.toString();
    }

    private float scoreValue(
            SolrDocument document) {

        Object value =
                document.getFieldValue(
                        "score"
                );

        if (value instanceof Number number) {
            return number.floatValue();
        }

        return 0.0f;
    }
}
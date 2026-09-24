package kbee.rag.thesaurus;

import java.util.Collection;
import java.util.List;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SolrThesaurusDao
        implements ThesaurusDao {

    private final SolrClient solrClient;

    private final String thesaurusCore;

    public SolrThesaurusDao(
            SolrClient solrClient,
            @Value("${solr.target.core}")
            String thesaurusCore) {

        this.solrClient =
                solrClient;

        this.thesaurusCore =
                thesaurusCore;
    }

    @Override
    public List<ConceptRecord> getConcepts() {

        ModifiableSolrParams params =
                new ModifiableSolrParams();

        params.set(
                "q",
                "*:*"
        );

        params.set(
                "fq",
                "document_type:thesaurus"
        );

        params.set(
                "fl",
                "id,thesaurus_term,thesaurus_es_term,embedding"
        );

        params.set(
                "rows",
                20000
        );

        try {

            QueryRequest request =
                    new QueryRequest(
                            params,
                            SolrRequest.METHOD.POST
                    );

            QueryResponse response =
                    request.process(
                            solrClient,
                            thesaurusCore
                    );

            return response
                    .getResults()
                    .stream()
                    .map(this::toConceptRecord)
                    .toList();

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Error recuperando conceptos del tesauro desde Solr",
                    e
            );
        }
    }

    private ConceptRecord toConceptRecord(
            SolrDocument document) {

        return new ConceptRecord(
                stringValue(
                        document,
                        "id"
                ),
                stringValue(
                        document,
                        "thesaurus_term"
                ),
                vectorValue(
                        document,
                        "embedding"
                )
        );
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

        if (value instanceof Collection<?> collection) {

            if (collection.isEmpty()) {
                return null;
            }

            Object first =
                    collection.iterator()
                            .next();

            return first == null
                    ? null
                    : first.toString();
        }

        return value.toString();
    }

    private List<Float> vectorValue(
            SolrDocument document,
            String field) {

        Object value =
                document.getFieldValue(
                        field
                );

        if (value == null) {
            return List.of();
        }

        if (value instanceof Collection<?> collection) {

            return collection
                    .stream()
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .map(Number::floatValue)
                    .toList();
        }

        throw new IllegalArgumentException(
                "El campo "
                        + field
                        + " no contiene un vector: "
                        + value.getClass().getName()
        );
    }
}
package kbee.rag.segment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import kbee.rag.embedding.EmbeddingService;

@Component
@ConditionalOnProperty(
        prefix = "thesaurus-indexer",
        name = "enabled",
        havingValue = "true"
)
public class ThesaurusIndexerCommand
        implements CommandLineRunner {

    private static final int PAGE_SIZE = 500;
    private static final int INDEX_BATCH_SIZE = 100;

    private final SolrClient solrClient;
    private final EmbeddingService embeddingService;
    private final String segmentCore;

    public ThesaurusIndexerCommand(
            SolrClient solrClient,
            EmbeddingService embeddingService,
            @Value("${solr.target.core}") String segmentCore) {

        this.solrClient = solrClient;
        this.embeddingService = embeddingService;
        this.segmentCore = segmentCore;
    }

    @Override
    public void run(String... args)
            throws Exception {

        System.out.println(
                "===== THESAURUS INDEXER START ====="
        );

        /*
         * 1. Recuperamos todas las voces existentes
         *    en los SUMARIOS.
         */
        Set<String> terms =
                loadTerms();

        System.out.println(
                "Voces únicas encontradas: "
                + terms.size()
        );

        /*
         * 2. Creamos un documento Solr por voz,
         *    con embedding propio.
         */
        indexTerms(terms);

        solrClient.commit(segmentCore);

        System.out.println(
                "===== THESAURUS INDEXER END ====="
        );
    }

    private Set<String> loadTerms()
            throws Exception {

        Set<String> terms =
                new LinkedHashSet<>();

        int start = 0;

        while (true) {

            SolrQuery query =
                    new SolrQuery("*:*");

            /*
             * IMPORTANTE:
             * solamente tomamos voces originales
             * asociadas a sumarios.
             */
            query.addFilterQuery(
                    "document_type:sumario"
            );

            query.addFilterQuery(
                    "thesaurus_term:[* TO *]"
            );

            query.setFields(
                    "thesaurus_term"
            );

            query.setStart(start);
            query.setRows(PAGE_SIZE);

            QueryResponse response =
                    solrClient.query(
                            segmentCore,
                            query
                    );

            if (response
                    .getResults()
                    .isEmpty()) {

                break;
            }

            for (SolrDocument doc :
                    response.getResults()) {

                Collection<Object> values =
                        doc.getFieldValues(
                                "thesaurus_term"
                        );

                if (values == null) {
                    continue;
                }

                for (Object value : values) {

                    if (value == null) {
                        continue;
                    }

                    String term =
                            value.toString()
                                    .trim();

                    if (!isValidTerm(term)) {
                        continue;
                    }

                    terms.add(term);
                }
            }

            int read =
                    response
                            .getResults()
                            .size();

            start += read;

            System.out.println(
                    "Sumarios procesados: "
                    + start
                    + " | voces únicas: "
                    + terms.size()
            );

            if (read < PAGE_SIZE) {
                break;
            }
        }

        return terms;
    }

    private void indexTerms(
            Set<String> terms)
            throws Exception {

        List<SolrInputDocument> batch =
                new ArrayList<>(
                        INDEX_BATCH_SIZE
                );

        int count = 0;

        for (String term : terms) {

            /*
             * Un embedding independiente
             * para cada voz del tesauro.
             *
             * Adaptá embed(...) al nombre exacto
             * de tu servicio actual.
             */
        	List<String> texts = List.of(term);

        	List<List<Float>> embeddings =
        	        embeddingService.embed(texts);

        	List<Float> embedding =
        	        embeddings.get(0);

            SolrInputDocument doc =
                    new SolrInputDocument();

            doc.addField(
                    "id",
                    createId(term)
            );

            doc.addField(
                    "document_type",
                    "thesaurus"
            );

            doc.addField(
                    "thesaurus_term",
                    term
            );

            doc.addField(
                    "embedding",
                    embedding
            );

            batch.add(doc);

            count++;

            if (batch.size()
                    >= INDEX_BATCH_SIZE) {

                solrClient.add(
                        segmentCore,
                        batch
                );

                batch.clear();

                System.out.println(
                        "Voces indexadas: "
                        + count
                );
            }
        }

        /*
         * Último batch.
         */
        if (!batch.isEmpty()) {

            solrClient.add(
                    segmentCore,
                    batch
            );
        }

        System.out.println(
                "Total voces indexadas: "
                + count
        );
    }

    private boolean isValidTerm(
            String term) {

        if (term == null
                || term.isBlank()) {
            return false;
        }

        /*
         * Evitamos entradas que contienen
         * accidentalmente texto completo
         * de un sumario.
         *
         * Ajustaremos este valor mirando
         * las voces reales.
         */
        if (term.length() > 300) {
            return false;
        }

        if (term.contains("\n")
                || term.contains("\r")) {
            return false;
        }

        return true;
    }

    private String createId(
            String term) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            term
                                    .toUpperCase()
                                    .trim()
                                    .getBytes(
                                            StandardCharsets.UTF_8
                                    )
                    );

            StringBuilder result =
                    new StringBuilder();

            for (byte b : hash) {

                result.append(
                        String.format(
                                "%02x",
                                b
                        )
                );
            }

            return "thesaurus-"
                    + result.substring(
                            0,
                            24
                    );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "No fue posible generar ID "
                    + "para la voz: "
                    + term,
                    e
            );
        }
    }
}
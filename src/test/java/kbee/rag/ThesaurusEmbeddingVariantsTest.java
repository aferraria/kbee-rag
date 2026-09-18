package kbee.rag;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import kbee.rag.embedding.EmbeddingService;

@SpringBootTest
class ThesaurusEmbeddingVariantsTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    void testVoluntadVicioVariants() {

        String query =
                "vicio de la voluntad";

        List<String> variants =
                List.of(
                        "VOLUNTAD. VICIO",
                        "VOLUNTAD VICIO",
                        "vicio de la voluntad",
                        "vicios de la voluntad",
                        "VOLUNTAD. VICIO vicio de la voluntad",
                        "VOLUNTAD. VICIO vicios de la voluntad",
                        "VOLUNTAD. VICIO vicio de la voluntad vicios de la voluntad"
                );

        compare(
                query,
                variants
        );
    }

    @Test
    void testRenunciaVariants() {

        String query =
                "anular una renuncia";

        List<String> variants =
                List.of(
                        "DERECHO > RENUNCIA",
                        "RENUNCIA",
                        "renuncia",
                        "renuncia de derechos",
                        "DERECHO > RENUNCIA renuncia",
                        "DERECHO > RENUNCIA renuncia de derechos",
                        "DERECHO > RENUNCIA renuncia renuncia de derechos"
                );

        compare(
                query,
                variants
        );
    }

    private void compare(
            String query,
            List<String> variants) {

        List<String> texts =
                new ArrayList<>();

        texts.add(query);
        texts.addAll(variants);

        List<List<Float>> embeddings =
                embeddingService
                        .embed(texts);

        assertNotNull(
                embeddings
        );

        if (embeddings.size()
                != texts.size()) {

            throw new IllegalStateException(
                    "Cantidad de embeddings incorrecta. "
                            + "Esperados="
                            + texts.size()
                            + ", recibidos="
                            + embeddings.size()
            );
        }

        List<Float> queryEmbedding =
                embeddings.get(0);

        System.out.println();
        System.out.println(
                "============================================================"
        );
        System.out.println(
                "QUERY: " + query
        );
        System.out.println(
                "============================================================"
        );

        for (int i = 0;
             i < variants.size();
             i++) {

            String variant =
                    variants.get(i);

            List<Float> variantEmbedding =
                    embeddings.get(
                            i + 1
                    );

            double similarity =
                    cosineSimilarity(
                            queryEmbedding,
                            variantEmbedding
                    );

            System.out.printf(
                    "%2d | %.6f | %s%n",
                    i + 1,
                    similarity,
                    variant
            );
        }

        System.out.println();
    }

    private double cosineSimilarity(
            List<Float> a,
            List<Float> b) {

        if (a.size() != b.size()) {

            throw new IllegalArgumentException(
                    "Los embeddings tienen distinta dimensión: "
                            + a.size()
                            + " != "
                            + b.size()
            );
        }

        double dot =
                0.0;

        double normA =
                0.0;

        double normB =
                0.0;

        for (int i = 0;
             i < a.size();
             i++) {

            double x =
                    a.get(i);

            double y =
                    b.get(i);

            dot +=
                    x * y;

            normA +=
                    x * x;

            normB +=
                    y * y;
        }

        if (normA == 0.0
                || normB == 0.0) {

            return 0.0;
        }

        return dot
                / (
                        Math.sqrt(normA)
                                * Math.sqrt(normB)
                );
    }
}
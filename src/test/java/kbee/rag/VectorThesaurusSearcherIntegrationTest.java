package kbee.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import kbee.rag.thesaurus.Concept;
import kbee.rag.thesaurus.ThesaurusSearcher;

@SpringBootTest
class VectorThesaurusSearcherIntegrationTest {

    @Autowired
    private ThesaurusSearcher thesaurusSearcher;

    @Test
    void shouldFindMedicalExpertEvidence() {

        String text =
                "quién es responsable si una pericia médica "
                + "no se realiza";

        long start =
                System.nanoTime();

        List<Concept> concepts =
                thesaurusSearcher
                        .findCandidates(text)
                        .block();

        long elapsed =
                System.nanoTime() - start;

        System.out.printf(
                "Vector search: %.3f ms%n",
                elapsed / 1_000_000.0
        );

        System.out.println();
        System.out.println(
                "===== VECTOR THESAURUS RESULTS ====="
        );

        for (int i = 0; i < concepts.size(); i++) {

            Concept concept =
                    concepts.get(i);

            System.out.printf(
                    "%2d. %.6f | %s%n",
                    i + 1,
                    concept.score(),
                    concept.term()
            );
        }

        System.out.println(
                "===================================="
        );

        assertThat(
                concepts.stream()
                        .map(Concept::term)
        ).contains(
                "PRUEBA PERICIAL MEDICA"
        );
    }
}
package kbee.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import kbee.rag.thesaurus.ThesaursService;
import kbee.rag.thesaurus.ThesaurusCandidate;

@SpringBootTest
class ThesaursServiceVectorTest {

    @Autowired
    private ThesaursService thesaursService;

    @Test
    void shouldFindVoluntadVicio() {

        List<ThesaurusCandidate> results =
                thesaursService
                        .findVectorCandidates(
                                "VOLUNTAD. VICIO",
                                30
                        );

        System.out.println(
                "===== voluntad. vicio ====="
        );

        for (int i = 0;
             i < results.size();
             i++) {

            ThesaurusCandidate candidate =
                    results.get(i);

            System.out.printf(
                    "%2d | %-80s | %.6f%n",
                    i + 1,
                    candidate.voice(),
                    candidate.score()
            );
        }

        assertThat(results)
                .extracting(
                        ThesaurusCandidate::voice
                )
                .contains(
                        "VOLUNTAD. VICIO"
                );
    }
    @Test
    void testVicioDeLaVoluntad() {

        String query =
                "vicio voluntad";


        List<ThesaurusCandidate> candidates =
                thesaursService.findVectorCandidates(
                        query,
                        100
                );

        printCandidates(
                query,
                candidates
        );
    }

    private void printCandidates(
            String query,
            List<ThesaurusCandidate> candidates) {

        System.out.println();

        System.out.println(
                "===== " + query + " ====="
        );

        for (int i = 0;
             i < candidates.size();
             i++) {

            ThesaurusCandidate candidate =
                    candidates.get(i);

            System.out.printf(
                    "%2d | %-80s | %.6f%n",
                    i + 1,
                    candidate.voice(),
                    candidate.score()
            );
        }
    }
}
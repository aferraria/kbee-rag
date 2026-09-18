package kbee.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import kbee.rag.search.ThesaursService;
import kbee.rag.search.ThesaurusCandidate;

@SpringBootTest
class ThesaursServiceIntegrationTest {

    @Autowired
    private ThesaursService thesaursService;

    @Test
    void shouldFindLexicalAndVectorCandidatesForRenunciaAndVicio() {

        String text =
                "Precedente sobre requisitos que exige "
                + "la Corte Suprema de Santa Fe "
                + "para anular una renuncia a reclamar daños "
                + "por vicios de la voluntad";

        List<ThesaurusCandidate> candidates =
                thesaursService.findCandidates(
                        text
                );

        candidates.forEach(candidate ->
                System.out.printf(
                        "%s | score=%.6f | normalized=%.6f | final=%.6f%n",
                        candidate.voice(),
                        candidate.score(),
                        candidate.normalizedScore(),
                        candidate.finalScore()
                )
        );

        assertThat(candidates)
                .extracting(
                        ThesaurusCandidate::voice
                )
                .contains(
                        "DERECHO > RENUNCIA",
                        "VOLUNTAD. VICIO"
                );
    }
}
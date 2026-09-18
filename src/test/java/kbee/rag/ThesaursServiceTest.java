package kbee.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.solr.client.solrj.SolrClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kbee.rag.embedding.EmbeddingService;
import kbee.rag.llm.LlmService;
import kbee.rag.search.ThesaursService;
import kbee.rag.search.ThesaurusCandidate;
import kbee.rag.search.ThesaurusCandidateFilter;

class ThesaursServiceTest {

    private EmbeddingService embeddingService;

    private SolrClient solrClient;

    private LlmService llmService;

    private ThesaurusCandidateFilter candidateFilter;

    private ThesaursService service;

    @BeforeEach
    void setUp() {

        embeddingService =
                mock(
                        EmbeddingService.class
                );

        solrClient =
                mock(
                        SolrClient.class
                );

        llmService =
                mock(
                        LlmService.class
                );

        candidateFilter =
                mock(
                        ThesaurusCandidateFilter.class
                );

        service =
                org.mockito.Mockito.spy(
                        new ThesaursService(
                                candidateFilter,
                                embeddingService,
                                llmService,
                                solrClient,
                                "kbee_segments",
                                "embedding"
                        )
                );
    }

    @Test
    void shouldMergeLexicalAndVectorCandidates() {

        String text =
                "Precedente sobre requisitos para anular "
                        + "una renuncia por vicios de la voluntad.";

        /*
         * findCandidates() envía:
         *
         * - texto global normalizado
         * - ventanas normalizadas
         *
         * en un único batch.
         */
        when(
                embeddingService.embed(
                        anyList()
                )
        )
        .thenAnswer(invocation -> {

            List<String> texts =
                    invocation.getArgument(0);

            return texts.stream()
                    .map(value ->
                            List.of(
                                    0.1f,
                                    0.2f,
                                    0.3f
                            )
                    )
                    .toList();
        });

        /*
         * Vectorial global.
         */
        doReturn(
                List.of(
                        new ThesaurusCandidate(
                                "DAÑOS Y PERJUICIOS",
                                0.95f
                        ),
                        new ThesaurusCandidate(
                                "RESPONSABILIDAD",
                                0.90f
                        )
                )
        )
        .when(service)
        .findVectorCandidates(
                anyList(),
                eq(100)
        );

        /*
         * Vectorial por ventana.
         */
        doReturn(
                List.of()
        )
        .when(service)
        .findVectorCandidates(
                anyList(),
                eq(20)
        );

        /*
         * Lexical por ventana.
         *
         * Ya no usamos:
         *
         * findLexicalCandidates(text, 30)
         *
         * sino:
         *
         * findLexicalCandidates(window, 10)
         */
        doReturn(
                List.of(
                        new ThesaurusCandidate(
                                "VOLUNTAD. VICIO",
                                5.0f
                        ),
                        new ThesaurusCandidate(
                                "DERECHO > RENUNCIA",
                                4.5f
                        )
                )
        )
        .when(service)
        .findLexicalCandidates(
                anyString(),
                eq(10)
        );

        List<ThesaurusCandidate> result =
                service.findCandidates(
                        text
                );

        assertThat(result)
                .extracting(
                        ThesaurusCandidate::voice
                )
                .contains(
                        "DAÑOS Y PERJUICIOS",
                        "RESPONSABILIDAD",
                        "VOLUNTAD. VICIO",
                        "DERECHO > RENUNCIA"
                );
    }
}
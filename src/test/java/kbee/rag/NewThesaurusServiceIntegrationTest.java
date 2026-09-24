package kbee.rag;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import kbee.rag.thesaurus.Concept;
import kbee.rag.thesaurus.ThesaurusService;
import kbee.rag.thesaurus.OldThesaursService;
import kbee.rag.thesaurus.ThesaurusCandidate;
import kbee.rag.thesaurus.ThesaurusSearcher;
import kbee.rag.thesaurus.WindowSplitter;

@SpringBootTest
class NewThesaurusServiceIntegrationTest {

    @Autowired
    private ThesaurusService newThesaurusService;
    
    @Autowired
    private WindowSplitter windowSplitter;
    
    @Autowired
    private OldThesaursService currentService;

    
    @Autowired
    @Qualifier("vectorThesaurusSearcher")
    private ThesaurusSearcher vectorSearcher;

    @Autowired
    @Qualifier("lexicalThesaurusSearcher")
    private ThesaurusSearcher lexicalSearcher;

    @Autowired
    private ThesaurusSearcher thesaurusSearcher;
    
    
    @Test
    void compareVectorAndLexicalSearcherPerformance() {

        String text = """
    Antecedentes sobre la responsabilidad del Estado por las inundaciones
    ocurridas en la ciudad de Santa Fe en 2003 como consecuencia del
    desborde del río Salado, los daños sufridos por los damnificados y
    la responsabilidad derivada de la falta de obras y medidas adecuadas
    para prevenir o mitigar la inundación.
                """;

        /*
         * ============================
         * VECTOR - FIRST CALL
         * ============================
         */

        long vectorFirstStart =
                System.nanoTime();

        List<Concept> vectorFirst =
                vectorSearcher
                        .findCandidates(text)
                        .block();

        long vectorFirstElapsed =
                System.nanoTime()
                        - vectorFirstStart;

        /*
         * ============================
         * VECTOR - SECOND CALL
         * ============================
         */

        long vectorSecondStart =
                System.nanoTime();

        List<Concept> vectorSecond =
                vectorSearcher
                        .findCandidates(text)
                        .block();

        long vectorSecondElapsed =
                System.nanoTime()
                        - vectorSecondStart;

        /*
         * ============================
         * LEXICAL - FIRST CALL
         * ============================
         */

        long lexicalFirstStart =
                System.nanoTime();

        List<Concept> lexicalFirst =
                lexicalSearcher
                        .findCandidates(text)
                        .block();

        long lexicalFirstElapsed =
                System.nanoTime()
                        - lexicalFirstStart;

        /*
         * ============================
         * LEXICAL - SECOND CALL
         * ============================
         */

        long lexicalSecondStart =
                System.nanoTime();

        List<Concept> lexicalSecond =
                lexicalSearcher
                        .findCandidates(text)
                        .block();

        long lexicalSecondElapsed =
                System.nanoTime()
                        - lexicalSecondStart;

        /*
         * ============================
         * PERFORMANCE
         * ============================
         */

        System.out.println();

        System.out.println(
                "===== SEARCHER PERFORMANCE ====="
        );

        System.out.printf(
                "VECTOR  #1: %.3f ms%n",
                vectorFirstElapsed / 1_000_000.0
        );

        System.out.printf(
                "VECTOR  #2: %.3f ms%n",
                vectorSecondElapsed / 1_000_000.0
        );

        System.out.printf(
                "LEXICAL #1: %.3f ms%n",
                lexicalFirstElapsed / 1_000_000.0
        );

        System.out.printf(
                "LEXICAL #2: %.3f ms%n",
                lexicalSecondElapsed / 1_000_000.0
        );

        /*
         * ============================
         * RESULT SIZE
         * ============================
         */

        System.out.println();

        System.out.println(
                "===== RESULT SIZE ====="
        );

        System.out.printf(
                "VECTOR  #1: %d%n",
                vectorFirst.size()
        );

        System.out.printf(
                "VECTOR  #2: %d%n",
                vectorSecond.size()
        );

        System.out.printf(
                "LEXICAL #1: %d%n",
                lexicalFirst.size()
        );

        System.out.printf(
                "LEXICAL #2: %d%n",
                lexicalSecond.size()
        );

        assertThat(vectorSecond)
                .isNotEmpty();

        assertThat(lexicalSecond)
                .isNotEmpty();
    }



        @Test
        void compareCurrentAndNewService() {

            String text = """
Antecedentes sobre la responsabilidad del Estado por las inundaciones
ocurridas en la ciudad de Santa Fe en 2003 como consecuencia del
desborde del río Salado, los daños sufridos por los damnificados y
la responsabilidad derivada de la falta de obras y medidas adecuadas
para prevenir o mitigar la inundación.
                    """;

            /*
             * ============================
             * CURRENT
             * ============================
             */

            long currentStart =
                    System.nanoTime();

            List<ThesaurusCandidate> current =
                    currentService.findCandidates(
                            text
                    );

            long currentElapsed =
                    System.nanoTime()
                            - currentStart;

            /*
             * ============================
             * NEW
             * ============================
             */

            
            
            long newStart =
                    System.nanoTime();

            
            List<Concept> newer  =
                    newThesaurusService
                            .findCandidates(text)
                            .doOnSubscribe(subscription ->
                                    System.out.println(
                                            ">>> SEARCH START"
                                    )
                            )
                            .doOnSuccess(result ->
                                    System.out.println(
                                            "<<< SEARCH OK: "
                                                    + result.size()
                                    )
                            )
                            .doOnError(error -> {
                                System.err.println(
                                        "===== SEARCH ERROR ====="
                                );
                                error.printStackTrace();
                            })
                            .block();

            long newElapsed =
                    System.nanoTime()
                            - newStart;

            /*
             * ============================
             * TIMES
             * ============================
             */

            System.out.println();
            System.out.println(
                    "===== PERFORMANCE ====="
            );

            System.out.printf(
                    "CURRENT: %.3f ms%n",
                    currentElapsed / 1_000_000.0
            );

            System.out.printf(
                    "NEW:     %.3f ms%n",
                    newElapsed / 1_000_000.0
            );

            /*
             * ============================
             * CURRENT RESULTS
             * ============================
             */

            System.out.println();
            System.out.println(
                    "===== CURRENT SERVICE ====="
            );

            for (int i = 0;
                    i < current.size();
                    i++) {

                ThesaurusCandidate candidate =
                        current.get(i);

                System.out.printf(
                        "%2d. %.6f | %s%n",
                        i + 1,
                        candidate.finalScore(),
                        candidate.voice()
                );
            }

            /*
             * ============================
             * NEW RESULTS
             * ============================
             */

            System.out.println();
            System.out.println(
                    "===== NEW SERVICE ====="
            );

            for (int i = 0;
                    i < newer.size();
                    i++) {

                Concept concept =
                        newer.get(i);

                System.out.printf(
                        "%2d. %.6f | %s%n",
                        i + 1,
                        concept.score(),
                        concept.term()
                );
            }

            /*
             * ============================
             * COMPARISON
             * ============================
             */

            printComparison(
                    current,
                    newer
            );

            assertThat(newer)
                    .isNotEmpty();
        }

        private void printComparison(
                List<ThesaurusCandidate> current,
                List<Concept> newer) {

            Map<String, Integer> currentRank =
                    new HashMap<>();

            for (int i = 0;
                    i < current.size();
                    i++) {

                currentRank.put(
                        current.get(i).voice(),
                        i + 1
                );
            }

            Map<String, Integer> newRank =
                    new HashMap<>();

            for (int i = 0;
                    i < newer.size();
                    i++) {

                newRank.put(
                        newer.get(i).term(),
                        i + 1
                );
            }

            System.out.println();
            System.out.println(
                    "===== IMPORTANT VOICES ====="
            );

            printVoice(
                    "PRUEBA PERICIAL MEDICA",
                    currentRank,
                    newRank
            );

            printVoice(
                    "PRUEBA > PRODUCCION > NEGLIGENCIA PROBATORIA",
                    currentRank,
                    newRank
            );

            printVoice(
                    "PRUEBA > NEGLIGENCIA PROBATORIA",
                    currentRank,
                    newRank
            );

            printVoice(
                    "IMPULSO PROCESAL",
                    currentRank,
                    newRank
            );

            printVoice(
                    "CADUCIDAD > IMPULSO PROCESAL",
                    currentRank,
                    newRank
            );

            /*
             * Sólo CURRENT
             */

            System.out.println();
            System.out.println(
                    "===== ONLY CURRENT ====="
            );

            current.stream()
                    .filter(candidate ->
                            !newRank.containsKey(
                                    candidate.voice()
                            )
                    )
                    .forEach(candidate ->
                            System.out.println(
                                    candidate.voice()
                            )
                    );

            /*
             * Sólo NEW
             */

            System.out.println();
            System.out.println(
                    "===== ONLY NEW ====="
            );

            newer.stream()
                    .filter(concept ->
                            !currentRank.containsKey(
                                    concept.term()
                            )
                    )
                    .forEach(concept ->
                            System.out.println(
                                    concept.term()
                            )
                    );
        }

        private void printVoice(
                String voice,
                Map<String, Integer> currentRank,
                Map<String, Integer> newRank) {

            System.out.printf(
                    "%-55s CURRENT=%-4s NEW=%-4s%n",
                    voice,
                    rank(
                            currentRank,
                            voice
                    ),
                    rank(
                            newRank,
                            voice
                    )
            );
        }

        private String rank(
                Map<String, Integer> ranks,
                String voice) {

            Integer rank =
                    ranks.get(voice);

            return rank == null
                    ? "-"
                    : "#" + rank;
        }
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    

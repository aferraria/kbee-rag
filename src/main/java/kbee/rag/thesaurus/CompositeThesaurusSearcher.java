package kbee.rag.thesaurus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Primary
public class CompositeThesaurusSearcher
        implements ThesaurusSearcher {

    private static final Logger log =
            LoggerFactory.getLogger(
                    ThesaurusSearcher.class
            );

    private static final int MAX_CANDIDATES = 60;

    /*
     * Igual que en el servicio viejo:
     *
     * 20 mejores del vector global
     * +
     * round-robin por ventanas
     * hasta completar 40 vectoriales.
     */
    private static final int GLOBAL_VECTOR_RESERVED = 20;

    private static final int MAX_VECTOR_CANDIDATES = 40;

    /*
     * Hasta 20 candidatos lexicales
     * seleccionados por round-robin.
     */
    private static final int MAX_LEXICAL_CANDIDATES = 20;

    private final WindowSplitter windowSplitter;

    private final ThesaurusSearcher vectorSearcher;

    private final ThesaurusSearcher lexicalSearcher;

    public CompositeThesaurusSearcher(
            WindowSplitter windowSplitter,

            @Qualifier("vectorThesaurusSearcher")
            ThesaurusSearcher vectorSearcher,

            @Qualifier("lexicalThesaurusSearcher")
            ThesaurusSearcher lexicalSearcher) {

        this.windowSplitter =
                windowSplitter;

        this.vectorSearcher =
                vectorSearcher;

        this.lexicalSearcher =
                lexicalSearcher;
    }

    @Override
    public Mono<List<Concept>> findCandidates(
            String text) {

        if (text == null
                || text.isBlank()) {

            return Mono.just(
                    List.of()
            );
        }

        /*
         * =============================================
         * 1. Ventanas
         * =============================================
         */

        List<String> windows =
                windowSplitter.split(
                        text
                );

        /*
         * =============================================
         * 2. Vector global
         *
         * Safety net sobre el texto completo.
         * =============================================
         */

        Mono<List<Concept>> globalVectorMono =
                vectorSearcher
                        .findCandidates(text)
                        .defaultIfEmpty(
                                List.of()
                        );

        /*
         * =============================================
         * 3. Vector por ventana
         * =============================================
         */

        Mono<List<List<Concept>>> vectorByWindowMono =
                searchWindows(
                        windows,
                        vectorSearcher
                );

        /*
         * =============================================
         * 4. Lexical por ventana
         * =============================================
         */

        Mono<List<List<Concept>>> lexicalByWindowMono =
                searchWindows(
                        windows,
                        lexicalSearcher
                );

        /*
         * Las tres ramas son independientes.
         */

        return Mono.zip(
                        globalVectorMono,
                        vectorByWindowMono,
                        lexicalByWindowMono
                )
                .map(tuple -> {

                    List<Concept> globalVector =
                            tuple.getT1();

                    List<List<Concept>> vectorByWindow =
                            tuple.getT2();

                    List<List<Concept>> lexicalByWindow =
                            tuple.getT3();

                    /*
                     * =============================================
                     * 5. Selección vectorial
                     *
                     * 20 globales
                     * +
                     * round-robin hasta 40.
                     * =============================================
                     */

                    List<Concept> vectorSelected =
                            selectVectorCandidates(
                                    globalVector,
                                    vectorByWindow
                            );

                    /*
                     * =============================================
                     * 6. Selección lexical
                     *
                     * Round-robin hasta 20.
                     * =============================================
                     */

                    List<Concept> lexicalSelected =
                            roundRobin(
                                    lexicalByWindow,
                                    MAX_LEXICAL_CANDIDATES,
                                    Set.of()
                            );

                    /*
                     * =============================================
                     * 7. Unión final
                     * =============================================
                     */

                    List<Concept> result =
                            mergeFinal(
                                    vectorSelected,
                                    lexicalSelected,
                                    globalVector,
                                    vectorByWindow,
                                    lexicalByWindow
                            );

                    /*
                     * El merge preserva el orden de inserción
                     * necesario durante la selección.
                     *
                     * Una vez terminada la selección,
                     * ordenamos los candidatos finales por score
                     * para que cualquier limit posterior tome
                     * realmente los candidatos mejor puntuados.
                     */

                    List<Concept> sortedResult =
                            result.stream()
                                    .sorted(
                                            Comparator
                                                    .comparingDouble(
                                                            Concept::score
                                                    )
                                                    .reversed()
                                    )
                                    .toList();

                    logResults(
                            text,
                            globalVector,
                            vectorByWindow,
                            lexicalByWindow,
                            sortedResult
                    );

                    return sortedResult;
                });
    }

    /*
     * =============================================
     * Ejecuta el searcher para cada ventana.
     *
     * concatMap mantiene el orden original
     * de las ventanas.
     * =============================================
     */

    private Mono<List<List<Concept>>> searchWindows(
            List<String> windows,
            ThesaurusSearcher searcher) {

        if (windows == null
                || windows.isEmpty()) {

            return Mono.just(
                    List.of()
            );
        }

        return Flux.fromIterable(
                        windows
                )
                .flatMapSequential(
                        window ->
                                searcher
                                        .findCandidates(window)
                                        .defaultIfEmpty(
                                                List.of()
                                        ),
                        8
                )
                .collectList();
    }

    /*
     * =============================================
     * Selección vectorial
     *
     * Replica el servicio viejo:
     *
     * 1. top 20 del ranking global.
     * 2. round-robin por ventanas hasta 40.
     * =============================================
     */

    private List<Concept> selectVectorCandidates(
            List<Concept> globalVector,
            List<List<Concept>> vectorByWindow) {

        LinkedHashMap<String, Concept> selected =
                new LinkedHashMap<>();

        /*
         * Primero los 20 globales.
         */

        addCandidates(
                selected,
                globalVector,
                GLOBAL_VECTOR_RESERVED
        );

        /*
         * Después round-robin por ventana.
         *
         * Importante:
         * deduplicamos también contra los
         * 20 globales ya seleccionados.
         */

        Set<String> alreadySelected =
                new HashSet<>(
                        selected.keySet()
                );

        List<Concept> diversified =
                roundRobin(
                        vectorByWindow,
                        MAX_VECTOR_CANDIDATES
                                - selected.size(),
                        alreadySelected
                );

        for (Concept concept :
                diversified) {

            selected.putIfAbsent(
                    concept.term(),
                    concept
            );

            if (selected.size()
                    >= MAX_VECTOR_CANDIDATES) {

                break;
            }
        }

        return List.copyOf(
                selected.values()
        );
    }

    /*
     * =============================================
     * Round-robin
     *
     * rank 0:
     *   ventana 1 candidato 1
     *   ventana 2 candidato 1
     *   ventana 3 candidato 1
     *
     * rank 1:
     *   ventana 1 candidato 2
     *   ventana 2 candidato 2
     *   ventana 3 candidato 2
     *   ...
     *
     * hasta alcanzar max.
     * =============================================
     */

    private List<Concept> roundRobin(
            List<List<Concept>> candidatesByWindow,
            int max,
            Set<String> excludedTerms) {

        if (max <= 0
                || candidatesByWindow == null
                || candidatesByWindow.isEmpty()) {

            return List.of();
        }

        LinkedHashMap<String, Concept> selected =
                new LinkedHashMap<>();

        Set<String> terms =
                new HashSet<>();

        if (excludedTerms != null) {
            terms.addAll(
                    excludedTerms
            );
        }

        int rank = 0;

        while (selected.size() < max) {

            boolean foundCandidate =
                    false;

            for (List<Concept> candidates :
                    candidatesByWindow) {

                if (candidates == null
                        || rank >= candidates.size()) {

                    continue;
                }

                /*
                 * Igual que el viejo:
                 * foundCandidate indica que existe
                 * un candidato en ese rank aunque
                 * luego resulte duplicado.
                 */

                foundCandidate =
                        true;

                Concept candidate =
                        candidates.get(rank);

                if (!isValid(
                        candidate
                )) {

                    continue;
                }

                String term =
                        candidate.term();

                if (terms.add(term)) {

                    selected.put(
                            term,
                            candidate
                    );

                    if (selected.size()
                            >= max) {

                        break;
                    }
                }
            }

            /*
             * Ninguna ventana tiene candidatos
             * para este rank.
             */

            if (!foundCandidate) {
                break;
            }

            rank++;
        }

        return List.copyOf(
                selected.values()
        );
    }

    /*
     * =============================================
     * Unión final
     *
     * 40 vectoriales
     * +
     * 20 lexicales.
     *
     * Si existen duplicados entre ambos grupos,
     * rellenamos los lugares libres.
     * =============================================
     */

    private List<Concept> mergeFinal(
            List<Concept> vectorSelected,
            List<Concept> lexicalSelected,
            List<Concept> globalVector,
            List<List<Concept>> vectorByWindow,
            List<List<Concept>> lexicalByWindow) {

        LinkedHashMap<String, Concept> result =
                new LinkedHashMap<>();

        /*
         * Primero vectoriales.
         */

        addCandidates(
                result,
                vectorSelected,
                MAX_VECTOR_CANDIDATES
        );

        /*
         * Después lexicales.
         */

        addCandidates(
                result,
                lexicalSelected,
                MAX_LEXICAL_CANDIDATES
        );

        /*
         * Si vector y lexical tenían voces
         * repetidas quedan lugares libres.
         *
         * Primero rellenamos con el ranking
         * vectorial global.
         */

        fill(
                result,
                globalVector,
                MAX_CANDIDATES
        );

        /*
         * Después con los rankings vectoriales
         * de las ventanas.
         */

        fillRoundRobin(
                result,
                vectorByWindow,
                MAX_CANDIDATES
        );

        /*
         * Y finalmente lexicales de las ventanas.
         */

        fillRoundRobin(
                result,
                lexicalByWindow,
                MAX_CANDIDATES
        );

        return List.copyOf(
                result.values()
        );
    }

    private void addCandidates(
            Map<String, Concept> result,
            List<Concept> candidates,
            int maxToAdd) {

        if (candidates == null
                || candidates.isEmpty()
                || maxToAdd <= 0) {

            return;
        }

        int added = 0;

        for (Concept candidate :
                candidates) {

            if (!isValid(
                    candidate
            )) {

                continue;
            }

            if (!result.containsKey(
                    candidate.term()
            )) {

                result.put(
                        candidate.term(),
                        candidate
                );

                added++;

                if (added >= maxToAdd) {
                    break;
                }
            }
        }
    }

    private void fill(
            Map<String, Concept> result,
            List<Concept> candidates,
            int maxTotal) {

        if (candidates == null
                || candidates.isEmpty()) {

            return;
        }

        for (Concept candidate :
                candidates) {

            if (result.size()
                    >= maxTotal) {

                break;
            }

            if (!isValid(
                    candidate
            )) {

                continue;
            }

            result.putIfAbsent(
                    candidate.term(),
                    candidate
            );
        }
    }

    /*
     * También hacemos el relleno respetando
     * diversidad por ventana.
     */

    private void fillRoundRobin(
            Map<String, Concept> result,
            List<List<Concept>> candidatesByWindow,
            int maxTotal) {

        if (candidatesByWindow == null
                || candidatesByWindow.isEmpty()) {

            return;
        }

        int rank = 0;

        while (result.size() < maxTotal) {

            boolean foundCandidate =
                    false;

            for (List<Concept> candidates :
                    candidatesByWindow) {

                if (candidates == null
                        || rank >= candidates.size()) {

                    continue;
                }

                foundCandidate =
                        true;

                Concept candidate =
                        candidates.get(rank);

                if (!isValid(
                        candidate
                )) {

                    continue;
                }

                result.putIfAbsent(
                        candidate.term(),
                        candidate
                );

                if (result.size()
                        >= maxTotal) {

                    break;
                }
            }

            if (!foundCandidate) {
                break;
            }

            rank++;
        }
    }

    private boolean isValid(
            Concept concept) {

        return concept != null
                && concept.term() != null
                && !concept.term().isBlank();
    }

    /*
     * =============================================
     * Diagnóstico
     * =============================================
     */

    private void logResults(
            String text,
            List<Concept> globalVector,
            List<List<Concept>> vectorByWindow,
            List<List<Concept>> lexicalByWindow,
            List<Concept> result) {

        if (!log.isDebugEnabled()) {
            return;
        }

        log.debug(
                "===== THESAURUS QUERY ====="
        );

        log.debug(
                "TEXT: {}",
                text
        );

        log.debug(
                "WINDOWS: {}",
                vectorByWindow.size()
        );

        log.debug(
                "===== GLOBAL VECTOR ====="
        );

        logCandidates(
                globalVector
        );

        log.debug(
                "===== FINAL ====="
        );

        logCandidates(
                result
        );
    }

    private void logCandidates(
            List<Concept> candidates) {

        if (candidates == null) {
            return;
        }

        for (int i = 0;
                i < candidates.size();
                i++) {

            log.debug(
                    "{}. {}",
                    i + 1,
                    candidates.get(i)
            );
        }
    }
}
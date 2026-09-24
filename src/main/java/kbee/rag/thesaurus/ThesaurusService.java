package kbee.rag.thesaurus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ThesaurusService {

    private static final int GLOBAL_RESERVED = 20;

    private static final int MAX_VECTOR_CANDIDATES = 60;

    private final WindowSplitter windowSplitter;

    private final ThesaurusSearcher thesaurusSearcher;

    public ThesaurusService(
            WindowSplitter windowSplitter,
            ThesaurusSearcher thesaurusSearcher) {

        this.windowSplitter =
                windowSplitter;

        this.thesaurusSearcher =
                thesaurusSearcher;
    }
    
    public Mono<ConceptList> candidates(
            String question) {

        if (question == null
                || question.isBlank()) {

            return Mono.just(
                    new ConceptList(
                            List.of()
                    )
            );
        }

        return findCandidates(question)
                .map(candidates -> {

                    double bestScore =
                            candidates.stream()
                                    .mapToDouble(Concept::score)
                                    .max()
                                    .orElse(0.0);

                    double threshold =
                            bestScore * 0.80;

                    List<Concept> concepts =
                            candidates.stream()
                                    .filter(concept ->
                                            concept.score()
                                                    >= threshold
                                    )
                                    .limit(100)
                                    .toList();

                    return new ConceptList(
                            concepts
                    );
                });
    }

    public Mono<List<Concept>> findCandidates(
            String text) {

        if (text == null
                || text.isBlank()) {

            return Mono.just(
                    List.of()
            );
        }

        List<String> windows =
                windowSplitter.split(
                        text
                );

        Mono<List<Concept>> globalSearch =
                thesaurusSearcher
                        .findCandidates(
                                text
                        );

        Mono<List<List<Concept>>> windowSearches =
                Flux.fromIterable(
                                windows
                        )
                        .flatMapSequential(
                                thesaurusSearcher::findCandidates,
                                4
                        )
                        .collectList();

        return Mono.zip(
                        globalSearch,
                        windowSearches
                )
                .map(tuple ->
                        selectCandidates(
                                tuple.getT1(),
                                tuple.getT2()
                        )
                );
    }

    private List<Concept> selectCandidates(
            List<Concept> globalCandidates,
            List<List<Concept>> windowCandidates) {

        List<Concept> selected =
                new ArrayList<>();

        Set<String> selectedTerms =
                new HashSet<>();

        for (Concept candidate :
                globalCandidates) {

            if (selected.size()
                    >= GLOBAL_RESERVED) {
                break;
            }

            addIfAbsent(
                    selected,
                    selectedTerms,
                    candidate
            );
        }

        int position = 0;

        while (selected.size()
                < MAX_VECTOR_CANDIDATES) {

            boolean foundCandidate =
                    false;

            for (List<Concept> ranking :
                    windowCandidates) {

                if (position
                        >= ranking.size()) {
                    continue;
                }

                foundCandidate =
                        true;

                Concept candidate =
                        ranking.get(
                                position
                        );

                addIfAbsent(
                        selected,
                        selectedTerms,
                        candidate
                );

                if (selected.size()
                        >= MAX_VECTOR_CANDIDATES) {
                    break;
                }
            }

            if (!foundCandidate) {
                break;
            }

            position++;
        }

        if (selected.size()
                < MAX_VECTOR_CANDIDATES) {

            for (Concept candidate :
                    globalCandidates) {

                addIfAbsent(
                        selected,
                        selectedTerms,
                        candidate
                );

                if (selected.size()
                        >= MAX_VECTOR_CANDIDATES) {
                    break;
                }
            }
        }

        return List.copyOf(
                selected
        );
    }

    private void addIfAbsent(
            List<Concept> selected,
            Set<String> selectedTerms,
            Concept candidate) {

        if (candidate == null
                || candidate.term() == null
                || candidate.term().isBlank()) {
            return;
        }

        if (selectedTerms.add(
                candidate.term()
        )) {

            selected.add(
                    candidate
            );
        }
    }
}
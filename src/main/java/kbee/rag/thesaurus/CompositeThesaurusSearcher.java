package kbee.rag.thesaurus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
@Primary
public class CompositeThesaurusSearcher
        implements ThesaurusSearcher {

    private static final int MAX_VECTOR_CANDIDATES = 60;

    private static final int MAX_LEXICAL_CANDIDATES = 20;

    private final ThesaurusSearcher vectorSearcher;

    private final ThesaurusSearcher lexicalSearcher;

    public CompositeThesaurusSearcher(
            @Qualifier("vectorThesaurusSearcher")
            ThesaurusSearcher vectorSearcher,

            @Qualifier("lexicalThesaurusSearcher")
            ThesaurusSearcher lexicalSearcher) {

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

        Mono<List<Concept>> vector =
                vectorSearcher
                        .findCandidates(text);

        Mono<List<Concept>> lexical =
                lexicalSearcher
                        .findCandidates(text);

        return Mono.zip(
                        vector,
                        lexical
                )
                .map(tuple ->
                        merge(
                                tuple.getT1(),
                                tuple.getT2()
                        )
                );
    }

    private List<Concept> merge(
            List<Concept> vector,
            List<Concept> lexical) {

        List<Concept> result =
                new ArrayList<>();

        Set<String> terms =
                new HashSet<>();

        addAll(
                result,
                terms,
                vector,
                MAX_VECTOR_CANDIDATES
        );

        addAll(
                result,
                terms,
                lexical,
                MAX_LEXICAL_CANDIDATES
        );

        return List.copyOf(
                result
        );
    }

    private void addAll(
            List<Concept> result,
            Set<String> terms,
            List<Concept> candidates,
            int max) {

        if (candidates == null
                || candidates.isEmpty()) {
            return;
        }

        int added = 0;

        for (Concept candidate :
                candidates) {

            if (candidate == null
                    || candidate.term() == null
                    || candidate.term().isBlank()) {

                continue;
            }

            if (terms.add(
                    candidate.term()
            )) {

                result.add(
                        candidate
                );

                added++;

                if (added >= max) {
                    break;
                }
            }
        }
    }
}
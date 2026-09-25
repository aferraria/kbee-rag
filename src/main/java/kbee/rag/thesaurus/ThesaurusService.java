package kbee.rag.thesaurus;

import java.util.List;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class ThesaurusService {

    private final ThesaurusSearcher thesaurusSearcher;

    public ThesaurusService(
            ThesaurusSearcher thesaurusSearcher) {

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
                .map(ConceptList::new);
    }

    public Mono<List<Concept>> findCandidates(
            String text) {

        if (text == null
                || text.isBlank()) {

            return Mono.just(
                    List.of()
            );
        }

        return thesaurusSearcher
                .findCandidates(
                        text
                );
    }
}
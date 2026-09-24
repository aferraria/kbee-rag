package kbee.rag.thesaurus;

import java.util.List;

import reactor.core.publisher.Mono;

public interface ThesaurusSearcher {

    Mono<List<Concept>> findCandidates(
            String text
    );
}
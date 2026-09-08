package kbee.rag.search;

import reactor.core.publisher.Mono;

public interface QuestionEnhancer {

    Mono<EnhancedQuestion> enhance(
            String question
    );
}
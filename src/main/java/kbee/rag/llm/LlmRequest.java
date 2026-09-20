package kbee.rag.llm;

import java.util.Map;

import reactor.core.publisher.Mono;

public interface LlmRequest<T> {

    String instructions();

    String input();

    Mono<T> execute();
    
    Map<String, Object> format();
    
    default String reasoningEffort() {
        return null;
    }
}
package kbee.rag.llm;

import reactor.core.publisher.Mono;

public interface LlmService {

    String providerId();

    Mono<String> generate(LlmRequest<?> request);
}
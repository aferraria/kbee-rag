package kbee.rag.llm;

import reactor.core.publisher.Mono;

public interface LlmService {

    Mono<String> generate(LlmRequest request);
}
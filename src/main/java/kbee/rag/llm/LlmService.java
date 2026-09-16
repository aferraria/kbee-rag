package kbee.rag.llm;

import reactor.core.publisher.Mono;

public interface LlmService {

    /**
     * Unique id of the LLM provider
     * (e.g. "ollama", "openai", "claude", "openrouter").
     * Used by the registry to route requests and to
     * resolve provider-specific prompts.
     */
    String providerId();

    Mono<String> generate(LlmRequest request);
}
package kbee.rag.ollama;

import kbee.rag.llm.LlmRequest;

public interface OllamaLlmRequest<T>
        extends LlmRequest<T> {
	
	default OllamaOptions options() {
		return OllamaOptions.defaults();
		}

}
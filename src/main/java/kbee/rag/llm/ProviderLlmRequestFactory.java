package kbee.rag.llm;

public interface ProviderLlmRequestFactory {

    <T> LlmRequestBuilder<T> builder(
            Class<? extends LlmRequest<T>> requestType
    );
}
package kbee.rag.openai;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import kbee.rag.config.InstructionProvider;
import kbee.rag.llm.LlmEnrichmentRequest;
import kbee.rag.llm.LlmRequest;
import kbee.rag.llm.LlmRequestBuilder;
import kbee.rag.llm.LlmService;
import kbee.rag.llm.ProviderLlmRequestFactory;
import kbee.rag.qwen.QwenEnrichmentRequest;

@Component
public class OpenAiLlmRequestFactory
        implements ProviderLlmRequestFactory {

    private final InstructionProvider instructionProvider;

    private final LlmService llmService;

    private final ObjectMapper objectMapper;

    public OpenAiLlmRequestFactory(
            InstructionProvider instructionProvider,
            OpenAiLlmService llmService,
            ObjectMapper objectMapper) {

        this.instructionProvider =
                instructionProvider;

        this.llmService =
                llmService;

        this.objectMapper =
                objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> LlmRequestBuilder<T> builder(
            Class<? extends LlmRequest<T>> requestType) {

        if (LlmEnrichmentRequest.class.equals(requestType)) {

            return (LlmRequestBuilder<T>)
                    QwenEnrichmentRequest
                            .builder()
                            .instructionProvider(
                                    instructionProvider
                            )
                            .llm(
                                    llmService
                            )
                            .objectMapper(
                                    objectMapper
                            );
        }

        throw new IllegalArgumentException(
                "Request type no soportado"
                        + requestType.getName()
        );
    }
}
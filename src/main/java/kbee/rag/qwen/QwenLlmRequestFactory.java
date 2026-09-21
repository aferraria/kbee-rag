package kbee.rag.qwen;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import kbee.rag.config.InstructionProvider;
import kbee.rag.llm.LlmAnalysisRequest;
import kbee.rag.llm.LlmBatchEnrichmentRequest;
import kbee.rag.llm.LlmEnrichmentRequest;
import kbee.rag.llm.LlmLawInterpretationRequest;
import kbee.rag.llm.LlmRequest;
import kbee.rag.llm.LlmRequestBuilder;
import kbee.rag.llm.LlmResponseRequest;
import kbee.rag.llm.LlmService;
import kbee.rag.llm.ProviderLlmRequestFactory;
import kbee.rag.ollama.OllamaLlmService;

@Component
public class QwenLlmRequestFactory
        implements ProviderLlmRequestFactory {

    private final InstructionProvider instructionProvider;

    private final LlmService llmService;

    private final ObjectMapper objectMapper;

    public QwenLlmRequestFactory(
            InstructionProvider instructionProvider,
            OllamaLlmService llmService,
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
        
        if (LlmBatchEnrichmentRequest.class.equals(requestType)) {

            return (LlmRequestBuilder<T>)
                    QwenBatchEnrichmentRequest
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
        
        if (LlmLawInterpretationRequest.class.equals(requestType)) {

            return (LlmRequestBuilder<T>)
                    QwenLawInterpretationRequest
                            .builder()
                            .instructionProvider(
                                    instructionProvider
                            )
                            .llm(
                                    llmService
                            );
        }
        
        if (LlmResponseRequest.class.equals(requestType)) {

            return (LlmRequestBuilder<T>)
                    QwenRagResponseRequest
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
        
        if (LlmAnalysisRequest.class.equals(requestType)) {

            return (LlmRequestBuilder<T>)
                    QwenAnalysisRequest
                            .builder()
                            .instructionProvider(
                                    instructionProvider
                            )
                            .llm(
                                    llmService
                            );
        }

        throw new IllegalArgumentException(
                "Request type no soportado"
                        + requestType.getName()
        );
    }
}
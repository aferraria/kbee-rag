package kbee.rag.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(
        prefix = "llm.openai",
        name = "enabled",
        havingValue = "true"
)
public class OpenAiLlmService
        extends OpenAiCompatibleLlmService {

    public static final String PROVIDER_ID = "openai";

    public OpenAiLlmService(
            ObjectMapper objectMapper,
            @Value("${llm.openai.base-url:https://api.openai.com/v1}")
            String baseUrl,
            @Value("${llm.openai.model}")
            String model,
            @Value("${llm.openai.api-key}")
            String apiKey,
            @Value("${llm.openai.connection-timeout-ms:10000}")
            long connectionTimeoutMs,
            @Value("${llm.openai.request-timeout-ms:120000}")
            long requestTimeoutMs) {

        super(
                objectMapper,
                baseUrl,
                model,
                apiKey,
                connectionTimeoutMs,
                requestTimeoutMs
        );
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }
}

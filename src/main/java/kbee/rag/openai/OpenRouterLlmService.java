package kbee.rag.openai;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Access to Qwen (3.5 / 3.x) or any other model through an
 * OpenAI-compatible aggregator such as OpenRouter or MindsHub.
 *
 * OpenRouter: base-url https://openrouter.ai/api/v1
 *             model    e.g. qwen/qwen3-235b-a22b
 * MindsHub:   point base-url at its OpenAI-compatible endpoint
 *             and set the Qwen model id it exposes.
 */
@Service
public class OpenRouterLlmService
        extends OpenAiCompatibleLlmService {

    public static final String PROVIDER_ID = "openrouter";

    private final String referer;

    private final String title;

    public OpenRouterLlmService(
            ObjectMapper objectMapper,
            @Value("${llm.openrouter.base-url:https://openrouter.ai/api/v1}")
            String baseUrl,
            @Value("${llm.openrouter.model}")
            String model,
            @Value("${llm.openrouter.api-key}")
            String apiKey,
            @Value("${llm.openrouter.referer:}")
            String referer,
            @Value("${llm.openrouter.title:KBEE RAG}")
            String title,
            @Value("${llm.openrouter.connection-timeout-ms:10000}")
            long connectionTimeoutMs,
            @Value("${llm.openrouter.request-timeout-ms:300000}")
            long requestTimeoutMs) {

        super(
                objectMapper,
                baseUrl,
                model,
                apiKey,
                connectionTimeoutMs,
                requestTimeoutMs
        );

        this.referer = referer;
        this.title = title;
    }

    @Override
    protected Map<String, String> extraHeaders() {

        Map<String, String> headers =
                new LinkedHashMap<>();

        /*
         * Optional attribution headers used by OpenRouter
         * for rankings / analytics.
         */
        if (referer != null && !referer.isBlank()) {
            headers.put("HTTP-Referer", referer);
        }
        if (title != null && !title.isBlank()) {
            headers.put("X-Title", title);
        }
        return headers;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }
}

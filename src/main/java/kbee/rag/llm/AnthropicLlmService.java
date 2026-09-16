package kbee.rag.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Anthropic Claude via the Messages API
 * (https://api.anthropic.com/v1/messages).
 */
@Service
@ConditionalOnProperty(
        prefix = "llm.claude",
        name = "enabled",
        havingValue = "true"
)
public class AnthropicLlmService
        implements LlmService {

    public static final String PROVIDER_ID = "claude";

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;

    private final String baseUrl;

    private final String model;

    private final String apiKey;

    private final int maxTokens;

    private final Duration requestTimeout;

    public AnthropicLlmService(
            ObjectMapper objectMapper,
            @Value("${llm.claude.base-url:https://api.anthropic.com/v1}")
            String baseUrl,
            @Value("${llm.claude.model}")
            String model,
            @Value("${llm.claude.api-key}")
            String apiKey,
            @Value("${llm.claude.max-tokens:8192}")
            int maxTokens,
            @Value("${llm.claude.connection-timeout-ms:10000}")
            long connectionTimeoutMs,
            @Value("${llm.claude.request-timeout-ms:300000}")
            long requestTimeoutMs) {

        this.objectMapper =
                objectMapper;

        this.baseUrl =
                baseUrl.endsWith("/")
                        ? baseUrl.substring(0, baseUrl.length() - 1)
                        : baseUrl;

        this.model =
                model == null
                        ? null
                        : model.trim();

        /*
         * Trim to defend against trailing newlines/whitespace
         * coming from env vars or key files: HTTP header values
         * must not contain line breaks.
         */
        this.apiKey =
                apiKey == null
                        ? null
                        : apiKey.trim();

        this.maxTokens =
                maxTokens;

        this.requestTimeout =
                Duration.ofMillis(requestTimeoutMs);

        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofMillis(
                                        connectionTimeoutMs
                                )
                        )
                        .build();

        System.out.println(
                "===== CLAUDE MODEL: "
                        + this.model
                        + " ====="
        );
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public Mono<String> generate(
            LlmRequest request) {

        return Mono.fromCallable(() ->
                executeGenerate(request)
        )
        .subscribeOn(
                Schedulers.boundedElastic()
        );
    }

    private String executeGenerate(
            LlmRequest request) {

        try {

            Map<String, Object> body =
                    new LinkedHashMap<>();

            body.put("model", model);
            body.put("max_tokens", maxTokens);
            body.put("temperature", 0.0);

            if (request.instructions() != null
                    && !request.instructions().isBlank()) {

                body.put(
                        "system",
                        request.instructions()
                );
            }

            String input =
                    request.input();

            if (request.format() != null) {

                /*
                 * Claude does not support response_format;
                 * we ask for JSON conforming to the schema
                 * in the user message instead.
                 */
                input = input
                        + "\n\nResponde únicamente con un JSON válido "
                        + "que cumpla el siguiente esquema:\n"
                        + objectMapper.writeValueAsString(
                                request.format()
                        );
            }

            body.put(
                    "messages",
                    List.of(
                            Map.of(
                                    "role", "user",
                                    "content", input
                            )
                    )
            );

            String json =
                    objectMapper.writeValueAsString(body);

            HttpRequest httpRequest =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            baseUrl + "/messages"
                                    )
                            )
                            .timeout(requestTimeout)
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .header(
                                    "x-api-key",
                                    apiKey
                            )
                            .header(
                                    "anthropic-version",
                                    "2023-06-01"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(json)
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            httpRequest,
                            HttpResponse.BodyHandlers
                                    .ofString()
                    );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                throw new LlmException(
                        "Claude respondió HTTP "
                                + response.statusCode()
                                + ": "
                                + response.body()
                );
            }

            JsonNode root =
                    objectMapper.readTree(
                            response.body()
                    );

            JsonNode text =
                    root.path("content")
                            .path(0)
                            .path("text");

            if (text.isMissingNode()
                    || text.isNull()) {

                throw new LlmException(
                        "Respuesta inválida de Claude: "
                                + response.body()
                );
            }

            return text.asText().trim();

        } catch (IOException e) {

            throw new LlmException(
                    "Error comunicándose con Claude",
                    e
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new LlmException(
                    "Llamada a Claude interrumpida",
                    e
            );
        }
    }
}

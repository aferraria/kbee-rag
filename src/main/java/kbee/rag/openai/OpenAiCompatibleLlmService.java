package kbee.rag.openai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kbee.rag.audit.Logger;
import kbee.rag.llm.LlmException;
import kbee.rag.llm.LlmRequest;
import kbee.rag.llm.LlmService;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Base implementation for every provider that exposes an
 * OpenAI-compatible /chat/completions API:
 *
 * OpenAI, OpenRouter, MindsHub, vLLM, LM Studio, etc.
 *
 * Subclasses define the provider id, credentials, endpoint
 * and optional provider-specific headers.
 */
public abstract class OpenAiCompatibleLlmService
        implements LlmService {

    private static final Logger logger =
            Logger.getLogger(
                    OpenAiCompatibleLlmService.class.getName()
            );

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;

    private final String baseUrl;

    private final String model;

    private final String apiKey;

    private final Duration requestTimeout;

    protected OpenAiCompatibleLlmService(
            ObjectMapper objectMapper,
            String baseUrl,
            String model,
            String apiKey,
            long connectionTimeoutMs,
            long requestTimeoutMs) {

        this.objectMapper =
                objectMapper;

        this.baseUrl =
                removeTrailingSlash(
                        baseUrl == null
                                ? null
                                : baseUrl.trim()
                );

        this.model =
                model == null
                        ? null
                        : model.trim();

        /*
         * Trim para evitar saltos de línea o espacios provenientes
         * de variables de entorno o archivos de configuración.
         *
         * Los valores de headers HTTP no pueden contener saltos
         * de línea.
         */
        this.apiKey =
                apiKey == null
                        ? null
                        : apiKey.trim();

        this.requestTimeout =
                Duration.ofMillis(
                        requestTimeoutMs
                );

        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofMillis(
                                        connectionTimeoutMs
                                )
                        )
                        .build();

        System.out.println(
                "===== "
                        + providerId().toUpperCase()
                        + " MODEL: "
                        + this.model
                        + " ====="
        );
    }

    /**
     * Extra provider-specific HTTP headers.
     *
     * For example:
     *
     * OpenRouter:
     * - HTTP-Referer
     * - X-Title
     */
    protected Map<String, String> extraHeaders() {
        return Map.of();
    }

    /**
     * Reactive entry point.
     *
     * java.net.http.HttpClient.send() is blocking, therefore
     * the actual HTTP request is executed on boundedElastic.
     */
    @Override
    public Mono<String> generate(
            LlmRequest<?> request) {

        return Mono.fromCallable(() ->
                executeGenerate(
                        request
                )
        )
        .subscribeOn(
                Schedulers.boundedElastic()
        );
    }

    /**
     * Blocking implementation of the HTTP request.
     */
    private String executeGenerate(
            LlmRequest<?> request) {

        try {

            /*
             * -------------------------------------------------
             * MESSAGES
             * -------------------------------------------------
             */

            List<Map<String, Object>> messages =
                    new ArrayList<>();

            if (request.instructions() != null
                    && !request.instructions().isBlank()) {

                messages.add(
                        Map.of(
                                "role",
                                "system",
                                "content",
                                request.instructions()
                        )
                );
            }

            messages.add(
                    Map.of(
                            "role",
                            "user",
                            "content",
                            request.input()
                    )
            );

            /*
             * -------------------------------------------------
             * REQUEST BODY
             * -------------------------------------------------
             */

            Map<String, Object> body =
                    new LinkedHashMap<>();

            body.put(
                    "model",
                    model
            );

            body.put(
                    "messages",
                    messages
            );

            body.put(
                    "reasoning_effort",
                    request.reasoningEffort() == null
                            || request.reasoningEffort().isBlank()
                            ? "low"
                            : request.reasoningEffort()
            );

            body.put(
                    "stream",
                    false
            );

            /*
             * Structured output.
             *
             * request.format() contains the JSON Schema
             * expected for the response.
             */
            if (request.format() != null) {

                body.put(
                        "response_format",
                        Map.of(
                                "type",
                                "json_schema",
                                "json_schema",
                                Map.of(
                                        "name",
                                        "response",
                                        "schema",
                                        request.format()
                                )
                        )
                );
            }

            String json =
                    objectMapper.writeValueAsString(
                            body
                    );

            logger.debug(
                    providerId()
                            + " request: "
                            + json
            );

            /*
             * -------------------------------------------------
             * HTTP REQUEST
             * -------------------------------------------------
             */

            HttpRequest.Builder builder =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            baseUrl
                                                    + "/chat/completions"
                                    )
                            )
                            .timeout(
                                    requestTimeout
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            );

            if (apiKey != null
                    && !apiKey.isBlank()) {

                builder.header(
                        "Authorization",
                        "Bearer "
                                + apiKey
                );
            }

            extraHeaders()
                    .forEach(
                            builder::header
                    );

            HttpRequest httpRequest =
                    builder.POST(
                            HttpRequest.BodyPublishers
                                    .ofString(
                                            json
                                    )
                    )
                    .build();

            /*
             * -------------------------------------------------
             * EXECUTION
             * -------------------------------------------------
             */

            long startTime =
                    System.currentTimeMillis();

            HttpResponse<String> response =
                    httpClient.send(
                            httpRequest,
                            HttpResponse.BodyHandlers
                                    .ofString()
                    );

            long elapsed =
                    System.currentTimeMillis()
                            - startTime;

            logger.debug(
                    "Roundtrip time for "
                            + providerId()
                            + " -> "
                            + elapsed
                            + " ms"
            );

            /*
             * -------------------------------------------------
             * HTTP STATUS
             * -------------------------------------------------
             */

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                throw new LlmException(
                        providerId()
                                + " respondió HTTP "
                                + response.statusCode()
                                + ": "
                                + response.body()
                );
            }

            /*
             * -------------------------------------------------
             * RESPONSE
             * -------------------------------------------------
             */

            JsonNode root =
                    objectMapper.readTree(
                            response.body()
                    );

            JsonNode content =
                    root.path("choices")
                            .path(0)
                            .path("message")
                            .path("content");

            if (content.isMissingNode()
                    || content.isNull()) {

                throw new LlmException(
                        "Respuesta inválida de "
                                + providerId()
                                + ": "
                                + response.body()
                );
            }

            return content
                    .asText()
                    .trim();

        } catch (IOException e) {

            throw new LlmException(
                    "Error comunicándose con "
                            + providerId(),
                    e
            );

        } catch (InterruptedException e) {

            Thread.currentThread()
                    .interrupt();

            throw new LlmException(
                    "Llamada a "
                            + providerId()
                            + " interrumpida",
                    e
            );
        }
    }

    private static String removeTrailingSlash(
            String value) {

        if (value != null
                && value.endsWith("/")) {

            return value.substring(
                    0,
                    value.length() - 1
            );
        }

        return value;
    }
}
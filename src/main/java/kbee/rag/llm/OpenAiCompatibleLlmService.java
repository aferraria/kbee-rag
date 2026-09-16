package kbee.rag.llm;

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

import kbee.rag.KbeeRagApplication;
import kbee.rag.audit.Logger;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Base implementation for every provider that exposes the
 * OpenAI-compatible {@code /chat/completions} API:
 * OpenAI itself, OpenRouter, MindsHub, vLLM, LM Studio, etc.
 *
 * Subclasses only define the provider id, credentials,
 * endpoint and optional extra headers.
 */
public abstract class OpenAiCompatibleLlmService
        implements LlmService {

	
	static private Logger logger = Logger.getLogger(OpenAiCompatibleLlmService.class.getName());
	
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
         * Trim to defend against trailing newlines/whitespace
         * coming from env vars or key files: HTTP header values
         * must not contain line breaks.
         */
        this.apiKey =
                apiKey == null
                        ? null
                        : apiKey.trim();

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
                "===== "
                        + providerId().toUpperCase()
                        + " MODEL: "
                        + this.model
                        + " ====="
        );
    }

    /**
     * Extra provider-specific HTTP headers
     * (e.g. OpenRouter's HTTP-Referer / X-Title).
     */
    protected Map<String, String> extraHeaders() {
        return Map.of();
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

            List<Map<String, Object>> messages =
                    new ArrayList<>();

            if (request.instructions() != null
                    && !request.instructions().isBlank()) {

                messages.add(
                        Map.of(
                                "role", "system",
                                "content", request.instructions()
                        )
                );
            }

            messages.add(
                    Map.of(
                            "role", "user",
                            "content", request.input()
                    )
            );

            Map<String, Object> body =
                    new LinkedHashMap<>();

            body.put("model", model);
            body.put("messages", messages);
            //body.put("temperature", 0.0);

            body.put(
                    "reasoning_effort",
                    request.reasoningEffort() == null
                            || request.reasoningEffort().isBlank()
                            ? "low"
                            : request.reasoningEffort()
            );
            body.put("stream", false);
          

            
            if (request.format() != null) {

                /*
                 * OpenAI-compatible structured output:
                 * request.format() is expected to be a
                 * JSON schema for the response.
                 */
                body.put(
                        "response_format",
                        Map.of(
                                "type", "json_schema",
                                "json_schema",
                                Map.of(
                                        "name", "response",
                                        "schema", request.format()
                                )
                        )
                );
            }

            String json =
                    objectMapper.writeValueAsString(body);

            
            logger.debug(
					providerId()
							+ " request: "
							+ json
			);
            
            
            HttpRequest.Builder builder =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            baseUrl
                                                    + "/chat/completions"
                                    )
                            )
                            .timeout(requestTimeout)
                            .header(
                                    "Content-Type",
                                    "application/json"
                            );

            if (apiKey != null
                    && !apiKey.isBlank()) {

                builder.header(
                        "Authorization",
                        "Bearer " + apiKey
                );
            }

            extraHeaders().forEach(builder::header);

            
            long startTime = System.currentTimeMillis();
            
            HttpRequest httpRequest =
                    builder.POST(
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
                        providerId()
                                + " respondió HTTP "
                                + response.statusCode()
                                + ": "
                                + response.body()
                );
            }

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

            
            logger.debug("Roundtrip time for "
					+ providerId()
					+ " -> "
					+ (System.currentTimeMillis() - startTime) 
					+ " ms"
			);
           
            
            return content.asText().trim();

        } catch (IOException e) {

            throw new LlmException(
                    "Error comunicándose con "
                            + providerId(),
                    e
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

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

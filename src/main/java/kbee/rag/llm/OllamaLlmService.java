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

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@ConditionalOnProperty(
        prefix = "llm.ollama",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OllamaLlmService
        implements LlmService {

    public static final String PROVIDER_ID = "ollama";

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;

    private final String baseUrl;

    private final String model;

    private final Duration requestTimeout;

    public OllamaLlmService(
            ObjectMapper objectMapper,
            @Value("${llm.ollama.base-url}")
            String baseUrl,
            @Value("${llm.ollama.model}")
            String model,
            @Value("${llm.ollama.connection-timeout-ms:5000}")
            long connectionTimeoutMs,
            @Value("${llm.ollama.request-timeout-ms:120000}")
            long requestTimeoutMs) {

        this.objectMapper =
                objectMapper;

        this.baseUrl =
                removeTrailingSlash(
                        baseUrl
                );

        this.model =
                model;

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
                "===== OLLAMA MODEL: "
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

            OllamaRequest requestBody =
                    new OllamaRequest(
                            model,
                            List.of(
                                    new Message(
                                            "system",
                                            request.instructions()
                                    ),
                                    new Message(
                                            "user",
                                            request.input()
                                    )
                            ),
                            false,
                            false,
                            request.format(),
//                            new Options(
//                                    0.0,
//                                    32768,
//                                    48768
//                            )
                            new Options(
                                    0.0,
                                    48000,  // num_ctx
                                    48768    // num_predict
                            )
                    );

            String json =
                    objectMapper.writeValueAsString(
                            requestBody
                    );

            HttpRequest httpRequest =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            baseUrl
                                                    + "/api/chat"
                                    )
                            )
                            .timeout(
                                    requestTimeout
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
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
                        "Ollama respondió HTTP "
                                + response.statusCode()
                                + ": "
                                + response.body()
                );
            }

            OllamaResponse ollamaResponse =
                    objectMapper.readValue(
                            response.body(),
                            OllamaResponse.class
                    );

            if (ollamaResponse.message() == null
                    || ollamaResponse.message()
                            .content() == null) {

                throw new LlmException(
                        "Respuesta inválida de Ollama: "
                                + response.body()
                );
            }

            return ollamaResponse
                    .message()
                    .content()
                    .trim();

        } catch (IOException e) {

            throw new LlmException(
                    "Error comunicándose con Ollama",
                    e
            );

        } catch (InterruptedException e) {

            Thread.currentThread()
                    .interrupt();

            throw new LlmException(
                    "La llamada a Ollama fue interrumpida",
                    e
            );
        }
    }
    
    private Map<String, Object> buildFormatSchema() {

        Map<String, Object> termProperties =
                new LinkedHashMap<>();

        termProperties.put(
                "termino",
                Map.of(
                        "type",
                        "string"
                )
        );

        termProperties.put(
                "justificacion",
                Map.of(
                        "type",
                        "string"
                )
        );

        Map<String, Object> termSchema =
                new LinkedHashMap<>();

        termSchema.put(
                "type",
                "object"
        );

        termSchema.put(
                "properties",
                termProperties
        );

        termSchema.put(
                "required",
                List.of(
                        "termino",
                        "justificacion"
                )
        );

        termSchema.put(
                "additionalProperties",
                false
        );

        Map<String, Object> properties =
                new LinkedHashMap<>();

        properties.put(
                "terminos",
                Map.of(
                        "type",
                        "array",
                        "items",
                        termSchema
                )
        );

        properties.put(
                "propositions",
                Map.of(
                        "type",
                        "array",
                        "items",
                        Map.of(
                                "type",
                                "string"
                        )
                )
        );

        Map<String, Object> schema =
                new LinkedHashMap<>();

        schema.put(
                "type",
                "object"
        );

        schema.put(
                "properties",
                properties
        );

        schema.put(
                "required",
                List.of(
                        "terminos",
                        "propositions"
                )
        );

        schema.put(
                "additionalProperties",
                false
        );

        return schema;
    }

    private String removeTrailingSlash(
            String value) {

        if (value.endsWith("/")) {

            return value.substring(
                    0,
                    value.length() - 1
            );
        }

        return value;
    }

    private record Options(
            double temperature,
            int num_ctx,
            int num_predict) {
    }

    private record OllamaRequest(
            String model,
            List<Message> messages,
            boolean stream,
            boolean think,
            Map<String, Object> format,
            Options options) {
    }

    private record Message(
            String role,
            String content) {
    }

    private record OllamaResponse(
            Message message) {
    }
}
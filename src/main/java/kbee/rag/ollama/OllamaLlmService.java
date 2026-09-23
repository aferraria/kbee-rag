package kbee.rag.ollama;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import kbee.rag.llm.LlmException;
import kbee.rag.llm.LlmRequest;
import kbee.rag.llm.LlmService;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class OllamaLlmService
        implements LlmService {
	
    private static final Logger log =
            LoggerFactory.getLogger(OllamaLlmService.class);;

    public static final String PROVIDER_ID = "ollama";

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;

    private final String baseUrl;

    private final String model;

    private final Duration requestTimeout;
    
    private final double temperature;

    
    /**
     * Semilla de muestreo para reproducibilidad.
     * null = no se envía (comportamiento por defecto
     * de Ollama, no determinista).
     */
    private final Long seed;
    
    
    public OllamaLlmService(
            ObjectMapper objectMapper,
            @Value("${llm.ollama.base-url}")
            String baseUrl,
            @Value("${llm.ollama.model}")
            String model,
            @Value("${llm.ollama.connection-timeout-ms:5000}")
            long connectionTimeoutMs,
            @Value("${llm.ollama.request-timeout-ms:120000}")
            long requestTimeoutMs,
            
            @Value("${llm.ollama.temperature:0.0}")
            double temperature,
            @Value("${llm.ollama.seed:#{null}}")
            Long seed) {

        this.objectMapper =
                objectMapper;

        this.baseUrl =
                removeTrailingSlash(
                        baseUrl
                );

        this.model =
                model;


        this.temperature =
                temperature;

        this.seed =
                seed;
        
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


        log.info(
                                "===== OLLAMA MODEL: "
                                        + this.model
                                        + " | temperature="
                                        + this.temperature
                                        + " | seed="
                                        + (this.seed == null
                                                ? "none"
                                                : this.seed)
                                        + " ====="
                        );
                        
        		
        }
    

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public Mono<String> generate(LlmRequest<?> request) {

        return Mono.fromCallable(() ->
                executeGenerate(request)
        )
        .subscribeOn(
                Schedulers.boundedElastic()
        );
    }

    private String executeGenerate(
            LlmRequest<?> request) {

        try {

        	OllamaLlmRequest<?> ollamaRequest =
        	        (OllamaLlmRequest<?>) request;
        	
        	OllamaOptions options =
        	        ollamaRequest.options();

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
        	                ollamaRequest.format(),
        	                new Options(
        	                        options.temperature(),
        	                        options.contextSize(),
        	                        options.maxOutputTokens(),
        	                        options.seed()
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
            
            log.info(
                    "OLLAMA PERF | request={} | model={} | " +
                    "promptTokens={} | outputTokens={} | " +
                    "promptMs={} | evalMs={} | loadMs={} | totalMs={}",
                    request.getClass().getSimpleName(),
                    model,
                    ollamaResponse.promptEvalCount(),
                    ollamaResponse.evalCount(),
                    ollamaResponse.promptEvalDuration() / 1_000_000,
                    ollamaResponse.evalDuration() / 1_000_000,
                    ollamaResponse.loadDuration() / 1_000_000,
                    ollamaResponse.totalDuration() / 1_000_000
            );

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
    
//    private Map<String, Object> buildFormatSchema() {
//
//        Map<String, Object> termProperties =
//                new LinkedHashMap<>();
//
//        termProperties.put(
//                "termino",
//                Map.of(
//                        "type",
//                        "string"
//                )
//        );
//
//        termProperties.put(
//                "justificacion",
//                Map.of(
//                        "type",
//                        "string"
//                )
//        );
//
//        Map<String, Object> termSchema =
//                new LinkedHashMap<>();
//
//        termSchema.put(
//                "type",
//                "object"
//        );
//
//        termSchema.put(
//                "properties",
//                termProperties
//        );
//
//        termSchema.put(
//                "required",
//                List.of(
//                        "termino",
//                        "justificacion"
//                )
//        );
//
//        termSchema.put(
//                "additionalProperties",
//                false
//        );
//
//        Map<String, Object> properties =
//                new LinkedHashMap<>();
//
//        properties.put(
//                "terminos",
//                Map.of(
//                        "type",
//                        "array",
//                        "items",
//                        termSchema
//                )
//        );
//
//        properties.put(
//                "propositions",
//                Map.of(
//                        "type",
//                        "array",
//                        "items",
//                        Map.of(
//                                "type",
//                                "string"
//                        )
//                )
//        );
//
//        Map<String, Object> schema =
//                new LinkedHashMap<>();
//
//        schema.put(
//                "type",
//                "object"
//        );
//
//        schema.put(
//                "properties",
//                properties
//        );
//
//        schema.put(
//                "required",
//                List.of(
//                        "terminos",
//                        "propositions"
//                )
//        );
//
//        schema.put(
//                "additionalProperties",
//                false
//        );
//
//        return schema;
//    }

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
            int num_predict,
            int seed) {
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

            Message message,

            @JsonProperty("total_duration")
            long totalDuration,

            @JsonProperty("load_duration")
            long loadDuration,

            @JsonProperty("prompt_eval_count")
            long promptEvalCount,

            @JsonProperty("prompt_eval_duration")
            long promptEvalDuration,

            @JsonProperty("eval_count")
            long evalCount,

            @JsonProperty("eval_duration")
            long evalDuration

    ) {
    }
}
package kbee.rag.embedding;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import kbee.rag.audit.Logger;


@Service
@ConditionalOnProperty(
        prefix = "embedding",
        name = "provider",
        havingValue = "qwen-local"
)
public class QwenLocalEmbeddingService implements EmbeddingService {

	static private Logger logger = Logger.getLogger( QwenLocalEmbeddingService.class.getName());
	
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final String baseUrl;
    private final String model;
    private final int expectedDimension;
    private final Duration requestTimeout;

    public QwenLocalEmbeddingService(
            ObjectMapper objectMapper,
            @Value("${embedding.qwen-local.base-url}") String baseUrl,
            @Value("${embedding.qwen-local.model}") String model,
            @Value("${embedding.dimension:1024}") int expectedDimension,
            @Value("${embedding.qwen-local.connection-timeout-ms:5000}") long connectionTimeoutMs,
            @Value("${embedding.qwen-local.request-timeout-ms:120000}") long requestTimeoutMs) {

        this.objectMapper = objectMapper;
        this.baseUrl = removeTrailingSlash(baseUrl);
        this.model = model;
        this.expectedDimension = expectedDimension;

        this.requestTimeout =
                Duration.ofMillis(requestTimeoutMs);

        this.httpClient =
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(
                                Duration.ofMillis(connectionTimeoutMs)
                        )
                        .build();

        logger.debug(
                "===== OLLAMA EMBEDDING MODEL: "
                        + this.model
                        + " ====="
        );
    }

    @Override
    public List<List<Float>> embed(List<String> texts) {

        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        validateTexts(texts);

        try {

            EmbeddingRequest requestBody =
                    new EmbeddingRequest(
                            model,
                            texts
                    );

            String json =
                    objectMapper.writeValueAsString(
                            requestBody
                    );

//            System.out.println(
//                    "Embedding model = " + model
//            );
//
//            System.out.println(
//                    "Embedding endpoint = "
//                            + baseUrl
//                            + "/api/embed"
//            );

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            baseUrl
                                                    + "/api/embed"
                                    )
                            )
                            .timeout(requestTimeout)
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .header(
                                    "Accept",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(json)
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers
                                    .ofString()
                    );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                throw new IllegalStateException(
                        "Ollama embeddings respondió HTTP "
                                + response.statusCode()
                                + ": "
                                + response.body()
                );
            }

            EmbeddingResponse embeddingResponse =
                    objectMapper.readValue(
                            response.body(),
                            EmbeddingResponse.class
                    );

            List<List<Float>> embeddings =
                    embeddingResponse.embeddings();

            validateEmbeddings(
                    texts,
                    embeddings
            );

            return embeddings;

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "La llamada a Ollama embeddings "
                            + "fue interrumpida",
                    e
            );

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Error comunicándose con Ollama "
                            + "embeddings en "
                            + baseUrl,
                    e
            );
        }
    }

    private void validateTexts(
            List<String> texts) {

        for (int i = 0; i < texts.size(); i++) {

            String text = texts.get(i);

            if (text == null || text.isBlank()) {

                throw new IllegalArgumentException(
                        "Texto nulo o vacío en posición "
                                + i
                );
            }
        }
    }

    private void validateEmbeddings(
            List<String> texts,
            List<List<Float>> embeddings) {

        if (embeddings == null) {

            throw new IllegalStateException(
                    "Ollama devolvió embeddings nulos"
            );
        }

        if (embeddings.size() != texts.size()) {

            throw new IllegalStateException(
                    "Cantidad incorrecta de embeddings. "
                            + "Esperados="
                            + texts.size()
                            + ", recibidos="
                            + embeddings.size()
            );
        }

        for (int i = 0; i < embeddings.size(); i++) {

            List<Float> embedding =
                    embeddings.get(i);

            if (embedding == null) {

                throw new IllegalStateException(
                        "Embedding nulo en posición "
                                + i
                );
            }

            if (embedding.size()
                    != expectedDimension) {

                throw new IllegalStateException(
                        "Dimensión incorrecta. "
                                + "Esperada="
                                + expectedDimension
                                + ", recibida="
                                + embedding.size()
                );
            }

            for (Float value : embedding) {

                if (value == null
                        || !Float.isFinite(value)) {

                    throw new IllegalStateException(
                            "Embedding contiene "
                                    + "un valor inválido"
                    );
                }
            }
        }
    }

    private static String removeTrailingSlash(
            String value) {

        while (value.endsWith("/")) {

            value =
                    value.substring(
                            0,
                            value.length() - 1
                    );
        }

        return value;
    }

    private record EmbeddingRequest(
            String model,
            List<String> input) {
    }

    private record EmbeddingResponse(
            List<List<Float>> embeddings) {
    }
}
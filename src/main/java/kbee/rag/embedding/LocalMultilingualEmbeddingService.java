package kbee.rag.embedding;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(
        prefix = "embedding",
        name = "provider",
        havingValue = "local-multilingual"
)
public class LocalMultilingualEmbeddingService
        implements EmbeddingService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final int expectedDimension;
    private final Duration requestTimeout;

    public LocalMultilingualEmbeddingService(
            ObjectMapper objectMapper,
            @Value("${embedding.local-multilingual.base-url}")
            String baseUrl,
            @Value("${embedding.dimension}")
            int expectedDimension,
            @Value("${embedding.local-multilingual.connection-timeout-ms:5000}")
            long connectionTimeoutMs,
            @Value("${embedding.local-multilingual.request-timeout-ms:120000}")
            long requestTimeoutMs) {

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "embedding.local-multilingual.base-url "
                    + "no está configurado"
            );
        }

        if (expectedDimension <= 0) {
            throw new IllegalArgumentException(
                    "embedding.dimension debe ser mayor que cero"
            );
        }

        if (connectionTimeoutMs <= 0) {
            throw new IllegalArgumentException(
                    "connection-timeout-ms debe ser mayor que cero"
            );
        }

        if (requestTimeoutMs <= 0) {
            throw new IllegalArgumentException(
                    "request-timeout-ms debe ser mayor que cero"
            );
        }

        this.objectMapper = objectMapper;
        this.expectedDimension = expectedDimension;
        this.requestTimeout =
                Duration.ofMillis(requestTimeoutMs);

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(
                        Duration.ofMillis(connectionTimeoutMs)
                )
                .build();
        
        this.endpoint = URI.create(
                removeTrailingSlash(baseUrl)
                + "/embeddings"
        );
    }

    @Override
    public List<List<Float>> embed(
            List<String> texts) {

        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        validateTexts(texts);

        EmbeddingRequest requestBody =
                new EmbeddingRequest(texts);

        String requestJson;

        try {
            requestJson =
                    objectMapper.writeValueAsString(
                            requestBody
                    );

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No se pudo serializar la solicitud "
                    + "de embeddings",
                    exception
            );
        }

        HttpResponse<String> response =
                sendRequest(requestJson);

        EmbeddingResponse embeddingResponse =
                parseResponse(response.body());

        validateResponse(
                texts,
                embeddingResponse
        );

        return embeddingResponse.embeddings();
    }

 
    private HttpResponse<String> sendRequest(
            String requestJson) {

        if (requestJson == null || requestJson.isBlank()) {
            throw new IllegalArgumentException(
                    "El JSON de embeddings está vacío"
            );
        }


        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(endpoint)
                        .timeout(requestTimeout)
                        .header(
                                "Content-Type",
                                "application/json; charset=UTF-8"
                        )
                        .header(
                                "Accept",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        requestJson,
                                        StandardCharsets.UTF_8
                                )
                        )
                        .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString(
                                    StandardCharsets.UTF_8
                            )
                    );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                throw new IllegalStateException(
                        "Error del servicio local de embeddings. "
                        + "HTTP=" + response.statusCode()
                        + ", endpoint=" + endpoint
                        + ", respuesta=" + response.body()
                );
            }

            return response;

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "La solicitud fue interrumpida",
                    exception
            );

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "No se pudo conectar con "
                    + endpoint,
                    exception
            );
        }
    }

    private EmbeddingResponse parseResponse(
            String responseBody) {

        if (responseBody == null
                || responseBody.isBlank()) {

            throw new IllegalStateException(
                    "El servicio local devolvió "
                    + "una respuesta vacía"
            );
        }

        try {
            return objectMapper.readValue(
                    responseBody,
                    EmbeddingResponse.class
            );

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No se pudo interpretar la respuesta "
                    + "del servicio local: "
                    + abbreviate(responseBody),
                    exception
            );
        }
    }

    private void validateTexts(
            List<String> texts) {

        for (int index = 0;
             index < texts.size();
             index++) {

            String text =
                    texts.get(index);

            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException(
                        "Texto nulo o vacío en posición "
                        + index
                );
            }
        }
    }

    private void validateResponse(
            List<String> texts,
            EmbeddingResponse response) {

        if (response == null) {
            throw new IllegalStateException(
                    "El servicio local devolvió "
                    + "una respuesta nula"
            );
        }

        if (response.embeddings() == null) {
            throw new IllegalStateException(
                    "El servicio local devolvió "
                    + "embeddings nulos"
            );
        }

        if (response.dimension()
                != expectedDimension) {

            throw new IllegalStateException(
                    "Dimensión informada incorrecta. "
                    + "Esperada="
                    + expectedDimension
                    + ", recibida="
                    + response.dimension()
            );
        }

        if (response.embeddings().size()
                != texts.size()) {

            throw new IllegalStateException(
                    "Cantidad incorrecta de embeddings. "
                    + "Esperados="
                    + texts.size()
                    + ", recibidos="
                    + response.embeddings().size()
            );
        }

        for (int embeddingIndex = 0;
             embeddingIndex
                     < response.embeddings().size();
             embeddingIndex++) {

            List<Float> embedding =
                    response.embeddings()
                            .get(embeddingIndex);

            validateEmbedding(
                    embeddingIndex,
                    embedding
            );
        }
    }

    private void validateEmbedding(
            int embeddingIndex,
            List<Float> embedding) {

        if (embedding == null) {
            throw new IllegalStateException(
                    "Embedding nulo en posición "
                    + embeddingIndex
            );
        }

        if (embedding.size()
                != expectedDimension) {

            throw new IllegalStateException(
                    "Dimensión incorrecta para embedding "
                    + embeddingIndex
                    + ". Esperada="
                    + expectedDimension
                    + ", recibida="
                    + embedding.size()
            );
        }

        for (int valueIndex = 0;
             valueIndex < embedding.size();
             valueIndex++) {

            Float value =
                    embedding.get(valueIndex);

            if (value == null
                    || !Float.isFinite(value)) {

                throw new IllegalStateException(
                        "Valor inválido en embedding "
                        + embeddingIndex
                        + ", posición="
                        + valueIndex
                        + ", valor="
                        + value
                );
            }
        }
    }

    private static boolean isSuccessful(
            int statusCode) {

        return statusCode >= 200
                && statusCode < 300;
    }

    private static String removeTrailingSlash(
            String value) {

        String result =
                value.trim();

        while (result.endsWith("/")) {
            result = result.substring(
                    0,
                    result.length() - 1
            );
        }

        return result;
    }

    private static String abbreviate(
            String value) {

        int maximumLength = 500;

        if (value == null
                || value.length()
                        <= maximumLength) {

            return value;
        }

        return value.substring(
                0,
                maximumLength
        ) + "...";
    }

    private record EmbeddingRequest(
            List<String> texts) {
    }

    private record EmbeddingResponse(
            String model,
            int dimension,
            int elapsed_ms,
            List<List<Float>> embeddings) {
    }
}
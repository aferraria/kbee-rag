package kbee.rag.embedding;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(
        prefix = "embedding",
        name = "provider",
        havingValue = "huggingface")
public class HuggingFaceEmbeddingService
        implements EmbeddingService {

    private static final int MAX_ATTEMPTS = 4;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final URI endpoint;
    private final String accessToken;
    private final int expectedDimension;
    private final Duration requestTimeout;

    public HuggingFaceEmbeddingService(
            ObjectMapper objectMapper,
            @Value("${embedding.huggingface.access-token}")
            String accessToken,
            @Value("${embedding.huggingface.model-id}")
            String modelId,
            @Value("${embedding.dimension}")
            int expectedDimension,
            @Value("${embedding.huggingface.base-url:"
                    + "https://router.huggingface.co/hf-inference/models}")
            String baseUrl,
            @Value("${embedding.huggingface.connection-timeout-ms:10000}")
            long connectionTimeoutMs,
            @Value("${embedding.huggingface.request-timeout-ms:120000}")
            long requestTimeoutMs) {

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException(
                    "embedding.huggingface.access-token no está configurado"
            );
        }

        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException(
                    "embedding.huggingface.model-id no está configurado"
            );
        }

        if (expectedDimension <= 0) {
            throw new IllegalArgumentException(
                    "embedding.dimension debe ser mayor que cero"
            );
        }

        this.objectMapper = objectMapper;
        this.accessToken = accessToken.trim();
        this.expectedDimension = expectedDimension;
        this.requestTimeout =
                Duration.ofMillis(requestTimeoutMs);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(
                        Duration.ofMillis(connectionTimeoutMs)
                )
                .build();

        this.endpoint = URI.create(
                removeTrailingSlash(baseUrl)
                + "/"
                + modelId
        );
    }

    @Override
    public List<List<Float>> embed(
            List<String> texts) {

        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        validateTexts(texts);
        
        System.out.println("Token presente: "
                + !accessToken.isBlank());

        System.out.println("Empieza con hf_: "
                + accessToken.startsWith("hf_"));

        System.out.println("Longitud: "
                + accessToken.length());

        HuggingFaceRequest request =
                new HuggingFaceRequest(
                        texts,
                        true,
                        true,
                        "right"
                );

        String requestJson;

        try {
            requestJson =
                    objectMapper.writeValueAsString(request);

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No se pudo serializar la solicitud "
                    + "de embeddings",
                    exception
            );
        }

        HttpResponse<String> response =
                sendWithRetry(requestJson);

        List<List<Float>> embeddings =
                parseResponse(response.body());

        validateEmbeddings(texts, embeddings);

        return embeddings;
    }

    private HttpResponse<String> sendWithRetry(
            String requestJson) {

        RuntimeException lastException = null;

        for (int attempt = 1;
                attempt <= MAX_ATTEMPTS;
                attempt++) {

            try {
                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(endpoint)
                                .timeout(requestTimeout)
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
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
                                                .ofString(requestJson)
                                )
                                .build();

                HttpResponse<String> response =
                        httpClient.send(
                                request,
                                HttpResponse.BodyHandlers
                                        .ofString()
                        );

                if (isSuccessful(response.statusCode())) {
                    return response;
                }

                if (!isRetryable(response.statusCode())
                        || attempt == MAX_ATTEMPTS) {

                    throw createApiException(response);
                }

                sleepBeforeRetry(attempt);

            } catch (InterruptedException exception) {

                Thread.currentThread().interrupt();

                throw new IllegalStateException(
                        "La solicitud de embeddings "
                        + "fue interrumpida",
                        exception
                );

            } catch (IOException exception) {

                lastException =
                        new IllegalStateException(
                                "No se pudo conectar con "
                                + "Hugging Face en "
                                + endpoint,
                                exception
                        );

                if (attempt == MAX_ATTEMPTS) {
                    throw lastException;
                }

                sleepBeforeRetry(attempt);
            }
        }

        throw new IllegalStateException(
                "No se pudo obtener embeddings",
                lastException
        );
    }

    private List<List<Float>> parseResponse(
            String responseBody) {

        if (responseBody == null
                || responseBody.isBlank()) {

            throw new IllegalStateException(
                    "Hugging Face devolvió una respuesta vacía"
            );
        }

        try {
            JsonNode root =
                    objectMapper.readTree(responseBody);

            /*
             * Los errores de Hugging Face normalmente vienen
             * como un objeto:
             *
             * {
             *   "error": "mensaje"
             * }
             */
            if (root.isObject()) {

                String error =
                        root.path("error").asText(null);

                throw new IllegalStateException(
                        error == null
                                ? "Respuesta inesperada de "
                                  + "Hugging Face: "
                                  + abbreviate(responseBody)
                                : "Error de Hugging Face: "
                                  + error
                );
            }

            if (!root.isArray()) {
                throw new IllegalStateException(
                        "Formato inesperado de respuesta: "
                        + abbreviate(responseBody)
                );
            }

            return objectMapper.readValue(
                    responseBody,
                    new TypeReference<
                            List<List<Float>>>() {
                    }
            );

        } catch (IllegalStateException exception) {
            throw exception;

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No se pudo interpretar la respuesta "
                    + "de Hugging Face: "
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

            String text = texts.get(index);

            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException(
                        "Texto nulo o vacío en posición "
                        + index
                );
            }
        }
    }

    private void validateEmbeddings(
            List<String> texts,
            List<List<Float>> embeddings) {

        if (embeddings == null) {
            throw new IllegalStateException(
                    "Hugging Face devolvió embeddings nulos"
            );
        }

        if (embeddings.size() != texts.size()) {
            throw new IllegalStateException(
                    "Cantidad incorrecta de embeddings. "
                    + "Esperados=" + texts.size()
                    + ", recibidos="
                    + embeddings.size()
            );
        }

        for (int index = 0;
                index < embeddings.size();
                index++) {

            List<Float> embedding =
                    embeddings.get(index);

            if (embedding == null) {
                throw new IllegalStateException(
                        "Embedding nulo en posición "
                        + index
                );
            }

            if (embedding.size()
                    != expectedDimension) {

                throw new IllegalStateException(
                        "Dimensión incorrecta para embedding "
                        + index
                        + ". Esperada="
                        + expectedDimension
                        + ", recibida="
                        + embedding.size()
                );
            }

            validateVectorValues(
                    index,
                    embedding
            );
        }
    }

    private void validateVectorValues(
            int vectorIndex,
            List<Float> embedding) {

        for (int valueIndex = 0;
                valueIndex < embedding.size();
                valueIndex++) {

            Float value =
                    embedding.get(valueIndex);

            if (value == null
                    || !Float.isFinite(value)) {

                throw new IllegalStateException(
                        "Valor inválido en embedding "
                        + vectorIndex
                        + ", posición "
                        + valueIndex
                        + ": "
                        + value
                );
            }
        }
    }

    private IllegalStateException createApiException(
            HttpResponse<String> response) {

        String message =
                extractErrorMessage(response.body());

        return new IllegalStateException(
                "Error de Hugging Face. HTTP="
                + response.statusCode()
                + ", endpoint="
                + endpoint
                + ", respuesta="
                + message
        );
    }

    private String extractErrorMessage(
            String responseBody) {

        if (responseBody == null
                || responseBody.isBlank()) {
            return "<vacía>";
        }

        try {
            JsonNode root =
                    objectMapper.readTree(responseBody);

            String error =
                    root.path("error").asText(null);

            if (error != null && !error.isBlank()) {
                return error;
            }

        } catch (IOException ignored) {
            // Se devuelve el cuerpo abreviado.
        }

        return abbreviate(responseBody);
    }

    private void sleepBeforeRetry(
            int attempt) {

        long delayMs =
                1000L * attempt;

        try {
            Thread.sleep(delayMs);

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "La espera para reintentar "
                    + "fue interrumpida",
                    exception
            );
        }
    }

    private static boolean isSuccessful(
            int statusCode) {

        return statusCode >= 200
                && statusCode < 300;
    }

    private static boolean isRetryable(
            int statusCode) {

        return statusCode == 429
                || statusCode == 500
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;
    }

    private static String removeTrailingSlash(
            String value) {

        String result = value;

        while (result.endsWith("/")) {
            result =
                    result.substring(
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
                || value.length() <= maximumLength) {
            return value;
        }

        return value.substring(
                0,
                maximumLength
        ) + "...";
    }

    private record HuggingFaceRequest(
            List<String> inputs,
            boolean normalize,
            boolean truncate,
            String truncation_direction) {
    }
}
package kbee.rag.reranker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import kbee.rag.search.ExpandedSource;
import kbee.rag.search.SegmentSearchResult;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Service
@ConditionalOnProperty(
        prefix = "reranker",
        name = "provider",
        havingValue = "openrouter"
)
public class OpenRouterRerankerService
        implements RerankerService {

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;

    private final String baseUrl;

    private final String apiKey;

    private final String model;

    private final Duration requestTimeout;

    public OpenRouterRerankerService(
            ObjectMapper objectMapper,
            @Value("${reranker.openrouter.base-url}")
            String baseUrl,
            @Value("${reranker.openrouter.api-key}")
            String apiKey,
            @Value("${reranker.openrouter.model:qwen/qwen3-reranker-8b}")
            String model,
            @Value("${reranker.openrouter.connection-timeout-ms:5000}")
            long connectionTimeoutMs,
            @Value("${reranker.openrouter.request-timeout-ms:120000}")
            long requestTimeoutMs) {

        this.objectMapper =
                objectMapper;

        this.baseUrl =
                removeTrailingSlash(
                        baseUrl
                );

        this.apiKey =
                apiKey;

        this.model =
                model;

        this.requestTimeout =
                Duration.ofMillis(
                        requestTimeoutMs
                );

        this.httpClient =
                HttpClient.newBuilder()
                        .version(
                                HttpClient.Version.HTTP_1_1
                        )
                        .connectTimeout(
                                Duration.ofMillis(
                                        connectionTimeoutMs
                                )
                        )
                        .build();
    }

    @Override
    public Mono<List<ExpandedSource>> rerank(
            RerankRequest request,
            int topK) {

        List<ExpandedSource> candidates =
                request.sources();

        if (candidates == null
                || candidates.isEmpty()) {

            return Mono.just(
                    List.of()
            );
        }

        return Mono.fromCallable(() -> {

            List<String> documents =
                    candidates.stream()
                            .map(
                                    this::buildRerankerText
                            )
                            .toList();

            String query =
                    request.question();

            RerankResponse rerankResponse =
                    executeRerank(
                            query,
                            documents,
                            topK
                    );

            if (rerankResponse.results() == null) {
                return List.<ExpandedSource>of();
            }

            return rerankResponse.results()
                    .stream()
                    .sorted(
                            (a, b) ->
                                    Double.compare(
                                            b.relevanceScore(),
                                            a.relevanceScore()
                                    )
                    )
                    .limit(topK)
                    .map(result -> {

                        int index =
                                result.index();

                        if (index < 0
                                || index >= candidates.size()) {

                            throw new IllegalStateException(
                                    "OpenRouter devolvió índice inválido: "
                                            + index
                                            + ", candidates="
                                            + candidates.size()
                            );
                        }

                        ExpandedSource candidate =
                                candidates.get(index);

                        return withRerankScore(
                                candidate,
                                result.relevanceScore()
                        );
                    })
                    .toList();

        })
        .subscribeOn(
                Schedulers.boundedElastic()
        )
        .retryWhen(
                Retry.backoff(
                        3,
                        Duration.ofSeconds(2)
                )
                .maxBackoff(
                        Duration.ofSeconds(10)
                )
                .filter(this::isRetryable)
        );
    }
    
    private boolean isRetryable(
            Throwable error) {

        if (!(error instanceof OpenRouterException ex)) {
            return false;
        }

        return switch (ex.statusCode()) {
            case 429, 502, 503, 504 -> true;
            default -> false;
        };
    }

    /*
     * =================================================
     * HTTP
     * =================================================
     */

    private RerankResponse executeRerank(
            String query,
            List<String> documents,
            int topK) {

        try {

            OpenRouterRerankRequest requestBody =
                    new OpenRouterRerankRequest(
                            model,
                            query,
                            documents,
                            topK
                    );

            String json =
                    objectMapper.writeValueAsString(
                            requestBody
                    );

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            baseUrl
                                                    + "/rerank"
                                    )
                            )
                            .timeout(
                                    requestTimeout
                            )
                            .header(
                                    "Authorization",
                                    "Bearer " + apiKey
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
                            request,
                            HttpResponse.BodyHandlers
                                    .ofString()
                    );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                throw new OpenRouterException(
                        response.statusCode(),
                        response.body()
                );
            }

            return objectMapper.readValue(
                    response.body(),
                    RerankResponse.class
            );

        } catch (OpenRouterException e) {

            // IMPORTANTE:
            // no envolverla para que retryWhen
            // pueda identificar el status HTTP.
            throw e;

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Error consultando OpenRouter reranker",
                    e
            );
        }
    }

    /*
     * =================================================
     * RERANK SCORE
     * =================================================
     */

    private ExpandedSource withRerankScore(
            ExpandedSource source,
            double score) {

        SegmentSearchResult selected =
                source.selected();

        SegmentSearchResult rerankedSelected =
                new SegmentSearchResult(
                        selected.id(),
                        selected.documentId(),
                        selected.documentTitle(),
                        selected.documentDate(),
                        selected.sectionId(),
                        selected.sectionTitle(),
                        selected.sectionPath(),
                        selected.segmentNumber(),
                        selected.sectionSegmentNumber(),
                        selected.text(),
                        (float) score
                );

        return new ExpandedSource(
                rerankedSelected,
                source.contextSegments()
        );
    }

    /*
     * =================================================
     * RERANKER TEXT
     * =================================================
     */

    private String buildRerankerText(
            ExpandedSource source) {

        StringBuilder text =
                new StringBuilder();

        SegmentSearchResult selected =
                source.selected();

        if (selected.documentTitle() != null
                && !selected.documentTitle().isBlank()) {

            text.append(
                    selected.documentTitle()
            );

            text.append(
                    "\n\n"
            );
        }

        if (selected.sectionPath() != null
                && !selected.sectionPath().isBlank()) {

            text.append(
                    selected.sectionPath()
            );

            text.append(
                    "\n\n"
            );
        }

        /*
         * Incluimos el segmento seleccionado.
         */
        if (selected.text() != null
                && !selected.text().isBlank()) {

            text.append(
                    selected.text()
            );

            text.append(
                    "\n\n"
            );
        }

        /*
         * Y los segmentos vecinos/contextuales.
         */
        for (SegmentSearchResult segment :
                source.contextSegments()) {

            if (segment.text() != null
                    && !segment.text().isBlank()) {

                text.append(
                        segment.text()
                );

                text.append(
                        "\n\n"
                );
            }
        }

        return text
                .toString()
                .trim();
    }

    /*
     * =================================================
     * UTILS
     * =================================================
     */

    private static String removeTrailingSlash(
            String value) {

        String result =
                value.trim();

        while (result.endsWith("/")) {

            result =
                    result.substring(
                            0,
                            result.length() - 1
                    );
        }

        return result;
    }

    /*
     * =================================================
     * DTO
     * =================================================
     */

    private record OpenRouterRerankRequest(

            String model,

            String query,

            List<String> documents,

            @JsonProperty("top_n")
            int topN) {
    }

    private record RerankResult(

            int index,

            @JsonProperty("relevance_score")
            double relevanceScore) {
    }

    private record RerankResponse(
            List<RerankResult> results) {
    }
}
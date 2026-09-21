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

import com.fasterxml.jackson.databind.ObjectMapper;

import kbee.rag.search.ExpandedSource;
import kbee.rag.search.SegmentSearchResult;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@ConditionalOnProperty(
        prefix = "reranker",
        name = "provider",
        havingValue = "local-qwen"
)
public class LocalQwenRerankerService
        implements RerankerService {

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;

    private final String baseUrl;

    private final Duration requestTimeout;

    public LocalQwenRerankerService(
            ObjectMapper objectMapper,
            @Value("${reranker.local.base-url}")
            String baseUrl,
            @Value("${reranker.local.connection-timeout-ms:5000}")
            long connectionTimeoutMs,
            @Value("${reranker.local.request-timeout-ms:120000}")
            long requestTimeoutMs) {

        this.objectMapper =
                objectMapper;

        this.baseUrl =
                removeTrailingSlash(
                        baseUrl
                );

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
    
//    @Override
//    public Mono<List<ExpandedSource>> rerankFinal(
//            RerankRequestOld request,
//            int topK) {
//    	return Mono.empty();
//    }
    
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
                    buildQuery(
                            request
                    );

            RerankResponse rerankResponse =
                    executeRerank(
                            query,
                            documents,
                            topK
                    );

            return rerankResponse.results()
                    .stream()
                    .limit(topK)
                    .map(result -> {

                        ExpandedSource candidate =
                                candidates.get(
                                        result.index()
                                );

                        return withRerankScore(
                                candidate,
                                result.score()
                        );
                    })
                    .toList();
        })
        .subscribeOn(
                Schedulers.boundedElastic()
        );
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
     * HTTP
     * =================================================
     */

    private RerankResponse executeRerank(
            String query,
            List<String> documents,
            int topK) {

        try {

            QwenRerankRequest requestBody =
                    new QwenRerankRequest(
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

                throw new IllegalStateException(
                        "Reranker HTTP "
                                + response.statusCode()
                                + ": "
                                + response.body()
                );
            }

            return objectMapper.readValue(
                    response.body(),
                    RerankResponse.class
            );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Error consultando reranker",
                    e
            );
        }
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
         * Importante:
         * incluimos también el segmento seleccionado.
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
    
//    private String buildQuery(
//            RerankRequest request) {
//
//        return """
//                %s
//
//                Consulta del usuario:
//                %s
//                """.formatted(
//                        request.instructions(),
//                        request.question()
//                );
//    }
    
    private String buildQuery(
            RerankRequest request) {

        return request.question();
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

    private record QwenRerankRequest(
            String query,
            List<String> documents,
            int top_k) {
    }

    private record RerankResult(
            int index,
            double score) {
    }

    private record RerankResponse(
            List<RerankResult> results) {
    }
}
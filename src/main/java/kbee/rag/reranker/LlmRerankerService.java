package kbee.rag.reranker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import kbee.rag.llm.LlmRequest;
import kbee.rag.llm.LlmService;
import kbee.rag.search.ExpandedSource;
import kbee.rag.search.SegmentSearchResult;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
        prefix = "reranker",
        name = "provider",
        havingValue = "qwen-llm"
)
public class LlmRerankerService
        implements RerankerService {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public LlmRerankerService(
            LlmService llmService,
            ObjectMapper objectMapper) {

        this.llmService =
                llmService;

        this.objectMapper =
                objectMapper;
    }

    @Override
    public Mono<List<ExpandedSource>> rerank(
            RerankRequest request,
            int topK) {

        //List<ExpandedSource> candidates =
        //        request.candidates();
  
        List<ExpandedSource> candidates =
                request.candidates()
                        .stream()
                        .limit(topK)
                        .toList();

        String input =
                buildInput(
                        request.question(),
                        candidates
                );

        System.out.println(
                "===== INPUT RERANK ====="
        );

        System.out.println(
                input
        );

        LlmRequest llmRequest =
                new LlmRequest(
                        request.instructions(),
                        input,
                        null
                );

        return llmService.generate(
                llmRequest
        )
        .doOnNext(response -> {

            System.out.println(
                    "===== RESPONSE RERANK ====="
            );

            System.out.println(
                    response
            );

        })
        .map(response ->
                parseRanking(
                        response,
                        candidates,
                        topK
                )
        )
        .doOnError(error -> {

            System.err.println(
                    "===== ERROR RERANK ====="
            );

            error.printStackTrace();

        });
    }
    
    private String buildInput(
            String question,
            List<ExpandedSource> candidates) {

        StringBuilder input =
                new StringBuilder();

        input.append(
                "CONSULTA\n\n"
        );

        input.append(
                question
        );

        input.append(
                "\n\nDOCUMENTOS CANDIDATOS\n\n"
        );

        for (int i = 0;
                i < candidates.size();
                i++) {

            ExpandedSource source =
                    candidates.get(i);

            input.append(
                    "===== DOCUMENTO "
                            + i
                            + " =====\n"
            );

            input.append(
                    buildRerankerText(
                            source
                    )
            );

            input.append(
                    "\n\n"
            );
        }

        return input.toString();
    }
    
    private List<ExpandedSource> parseRanking(
            String response,
            List<ExpandedSource> candidates,
            int topK) {

        try {

            RankingResponse ranking =
                    objectMapper.readValue(
                            response,
                            RankingResponse.class
                    );

            return ranking.ranking()
                    .stream()
                    .filter(result ->
                            result.index() >= 0
                                    && result.index()
                                            < candidates.size()
                    )
                    .limit(topK)
                    .map(result ->
                            withRerankScore(
                                    candidates.get(
                                            result.index()
                                    ),
                                    result.score()
                            )
                    )
                    .toList();

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Respuesta inválida del LLM reranker: "
                            + response,
                    e
            );
        }
    }
    

    private String buildRerankerText(
            ExpandedSource source) {

        StringBuilder text =
                new StringBuilder();

        SegmentSearchResult selected =
                source.selected();

        if (selected.documentTitle() != null
                && !selected.documentTitle().isBlank()) {

            text.append("Título: ")
                    .append(selected.documentTitle())
                    .append("\n\n");
        }

        if (selected.sectionPath() != null
                && !selected.sectionPath().isBlank()) {

            text.append("Sección: ")
                    .append(selected.sectionPath())
                    .append("\n\n");
        }

        if (selected.text() != null
                && !selected.text().isBlank()) {

            text.append(selected.text())
                    .append("\n\n");
        }

        source.contextSegments()
                .stream()
                .sorted(
                        java.util.Comparator.comparingInt(
                                SegmentSearchResult::segmentNumber
                        )
                )
                .forEach(segment -> {

                    if (segment.text() != null
                            && !segment.text().isBlank()) {

                        text.append(segment.text())
                                .append("\n\n");
                    }
                });

        return text
                .toString()
                .trim();
    }
    
    
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
    
    private record RankingResult(
            int index,
            double score) {
    }

    private record RankingResponse(
            List<RankingResult> ranking) {
    }
}
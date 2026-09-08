package kbee.rag.audit;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import kbee.rag.search.EnhancedQuestion;
import kbee.rag.search.ExpandedSource;
import kbee.rag.search.SegmentSearchResult;


public class AuditContext {

    private final String id =
            UUID.randomUUID().toString();

    private final OffsetDateTime startedAt =
            OffsetDateTime.now();

    private OffsetDateTime finishedAt;

    private String question;
    private Integer requestedTopK;

    private EnhancedQuestion enhancedQuestion;

    private List<SegmentSearchResult> searchResults =
            List.of();

    private List<ExpandedSource> expandedSources =
            List.of();

    private List<ExpandedSource> rerankedSources =
            List.of();

    private String answer;

    private Duration questionEnhancementDuration;
    private Duration searchDuration;
    private Duration sourceExpansionDuration;
    private Duration rerankDuration;
    private Duration answerGenerationDuration;

    private Map<String, Object> metadata =
            Map.of();

    public AuditContext(
            String question,
            Integer requestedTopK) {

        this.question = question;
        this.requestedTopK = requestedTopK;
    }

    public String id() {
        return id;
    }

    public OffsetDateTime startedAt() {
        return startedAt;
    }

    public String question() {
        return question;
    }

    public Integer requestedTopK() {
        return requestedTopK;
    }

    public EnhancedQuestion enhancedQuestion() {
        return enhancedQuestion;
    }

    public void enhancedQuestion(
            EnhancedQuestion enhancedQuestion) {

        this.enhancedQuestion =
                enhancedQuestion;
    }

    public void searchResults(
            List<SegmentSearchResult> searchResults) {

        this.searchResults =
                copy(searchResults);
    }

    public void expandedSources(
            List<ExpandedSource> expandedSources) {

        this.expandedSources =
                copy(expandedSources);
    }

    public void rerankedSources(
            List<ExpandedSource> rerankedSources) {

        this.rerankedSources =
                copy(rerankedSources);
    }

    public void answer(
            String answer) {

        this.answer = answer;
    }

    public void questionEnhancementDuration(
            Duration duration) {

        this.questionEnhancementDuration =
                duration;
    }

    public void searchDuration(
            Duration duration) {

        this.searchDuration =
                duration;
    }

    public void sourceExpansionDuration(
            Duration duration) {

        this.sourceExpansionDuration =
                duration;
    }

    public void rerankDuration(
            Duration duration) {

        this.rerankDuration =
                duration;
    }

    public void answerGenerationDuration(
            Duration duration) {

        this.answerGenerationDuration =
                duration;
    }

    public void metadata(
            Map<String, Object> metadata) {

        this.metadata =
                metadata == null
                        ? Map.of()
                        : Map.copyOf(metadata);
    }

    public AuditRecord finish() {

        finishedAt =
                OffsetDateTime.now();

        Duration totalDuration =
                Duration.between(
                        startedAt,
                        finishedAt
                );

        return new AuditRecord(
                id,
                startedAt,
                finishedAt,
                question,
                requestedTopK,
                enhancedQuestion,
                searchResults,
                expandedSources,
                rerankedSources,
                answer,
                new AuditTimings(
                        totalDuration,
                        questionEnhancementDuration,
                        searchDuration,
                        sourceExpansionDuration,
                        rerankDuration,
                        answerGenerationDuration
                ),
                metadata
        );
    }

    private static <T> List<T> copy(
            List<T> values) {

        return values == null
                ? List.of()
                : List.copyOf(values);
    }
}
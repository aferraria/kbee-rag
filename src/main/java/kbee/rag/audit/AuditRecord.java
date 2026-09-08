package kbee.rag.audit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import kbee.rag.search.EnhancedQuestion;
import kbee.rag.search.ExpandedSource;
import kbee.rag.search.SegmentSearchResult;

public record AuditRecord(

        String id,

        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,

        String question,
        Integer requestedTopK,

        EnhancedQuestion enhancedQuestion,

        List<SegmentSearchResult> searchResults,

        List<ExpandedSource> expandedSources,

        List<ExpandedSource> rerankedSources,

        String answer,

        AuditTimings timings,

        Map<String, Object> metadata

) {

    public AuditRecord {

        searchResults =
                searchResults == null
                        ? List.of()
                        : List.copyOf(searchResults);

        expandedSources =
                expandedSources == null
                        ? List.of()
                        : List.copyOf(expandedSources);

        rerankedSources =
                rerankedSources == null
                        ? List.of()
                        : List.copyOf(rerankedSources);

        metadata =
                metadata == null
                        ? Map.of()
                        : Map.copyOf(metadata);
    }
}
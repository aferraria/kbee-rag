package kbee.rag.search;

import java.util.List;

public record VectorSearchResponse(
        String query,
        int topK,
        int embeddingDimension,
        long elapsedMilliseconds,
        List<SegmentSearchResult> results) {
}
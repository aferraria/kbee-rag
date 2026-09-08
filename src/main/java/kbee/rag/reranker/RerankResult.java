package kbee.rag.reranker;

import kbee.rag.search.SegmentSearchResult;

public record RerankResult(
        SegmentSearchResult source,
        double rerankScore
) {
}
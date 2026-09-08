package kbee.rag.reranker;

import java.util.List;

import kbee.rag.search.ExpandedSource;

public record RerankRequest(
        String instructions,
        String question,
        List<ExpandedSource> candidates
) {
}
package kbee.rag.reranker;

import java.util.List;

import kbee.rag.search.ExpandedSource;
import reactor.core.publisher.Mono;

public record SimpleRerankRequest(
        String question,
        List<ExpandedSource> sources
) implements RerankRequest {

    @Override
    public Mono<List<ExpandedSource>> execute(
            RerankerService reranker) {

        return reranker.rerank(
                this,
                sources.size()
        );
    }
}
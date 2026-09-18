package kbee.rag.reranker;

import java.util.List;

import kbee.rag.search.ExpandedSource;
import reactor.core.publisher.Mono;

public interface RerankerService {

    Mono<List<ExpandedSource>> rerank(
            RerankRequest request,
            int topK
    );
    
    Mono<List<ExpandedSource>> rerankFinal(
            RerankRequest request,
            int topK
    );
}
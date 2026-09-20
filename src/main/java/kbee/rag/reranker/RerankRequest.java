package kbee.rag.reranker;

import java.util.List;

import kbee.rag.search.ExpandedSource;
import reactor.core.publisher.Mono;

public interface RerankRequest {
	
	String question();
	
	List<ExpandedSource> sources();
	
    Mono<List<ExpandedSource>> execute(
            RerankerService rerankerService
    );	
}

package kbee.rag.reranker;

import org.springframework.stereotype.Component;

@Component
public class RerankRequestBuilderFactory {
 
    public RerankRequestBuilderFactory() {
    }

    public RerankRequestBuilder builder() {
    	return BatchRerankRequest.builder();
    }
}
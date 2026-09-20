package kbee.rag.reranker;

import java.util.List;

import kbee.rag.search.ExpandedSource;

public interface RerankRequestBuilder {

    RerankRequestBuilder question(
            String question
    );

    RerankRequestBuilder sources(
            List<ExpandedSource> sources
    );

    RerankRequestBuilder topK(
            int topK
    );

    RerankRequest build();
}
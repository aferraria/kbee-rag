package kbee.rag.reranker;

import org.springframework.stereotype.Component;



    @Component
    public class RerankRequestBuilderFactory {

        private static final int BATCH_SIZE = 4;

        public RerankRequestBuilder builder() {

            return BatchRerankRequest
                    .builder()
                    .batchSize(BATCH_SIZE);
        }
    }
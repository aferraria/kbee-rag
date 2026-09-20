package kbee.rag.llm;


public interface LlmRequestBuilder<T> {

//    LlmRequestBuilder<T> sources(
//            List<ExpandedSource> sources
//    );
//    
//    LlmRequestBuilder<T> question(
//            String question
//    );
	
    LlmRequestBuilder<T> input(
            Object object
     );

    
    LlmRequestBuilder<T> llm(
            LlmService llm
     );

    LlmRequest<T> build();
}
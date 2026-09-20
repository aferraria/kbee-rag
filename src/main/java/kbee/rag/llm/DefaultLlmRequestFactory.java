package kbee.rag.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import kbee.rag.openai.OpenAiLlmRequestFactory;
import kbee.rag.qwen.QwenLlmRequestFactory;
@Component
public class DefaultLlmRequestFactory
        implements LlmRequestFactory {

    private final QwenLlmRequestFactory qwenFactory;
    private final OpenAiLlmRequestFactory openAiFactory;

    private final String defaultProvider;

    public DefaultLlmRequestFactory(
    		@Value("${llm.provider}") String defaultProvider,
    		QwenLlmRequestFactory qwenFactory,
    		OpenAiLlmRequestFactory  openAiFactory) {

            this.defaultProvider = 
            		defaultProvider;
            this.qwenFactory =
                    qwenFactory;
            this.openAiFactory =
                    openAiFactory;
    }
    
    @Override
    public String defaultProvider() {
        return defaultProvider;
    }

    @Override
    public <T> LlmRequestBuilder<T> builder(
            String provider,
            Class<? extends LlmRequest<T>> requestType) {

        return switch (provider) {

            case "ollama" ->
                    qwenFactory.builder(
                    	requestType
                    );

            case "openai" ->
		            openAiFactory.builder(
		            	requestType
		            );
            
            default ->
                    throw new IllegalArgumentException(
                            "LLM provider no soportado: "
                                    + provider
                    );
        };
    }
}
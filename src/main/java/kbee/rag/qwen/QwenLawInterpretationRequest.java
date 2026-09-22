package kbee.rag.qwen;

import java.util.Map;

import kbee.rag.config.InstructionProvider;
import kbee.rag.llm.LlmLawInterpretationRequest;
import kbee.rag.llm.LlmRequestBuilder;
import kbee.rag.llm.LlmService;
import kbee.rag.ollama.OllamaLlmRequest;
import kbee.rag.ollama.OllamaOptions;
import reactor.core.publisher.Mono;

public class QwenLawInterpretationRequest
        implements OllamaLlmRequest<Boolean>, 
		LlmLawInterpretationRequest {

    private final String text;
    
    private final LlmService llm;

    private final InstructionProvider instructionProvider;

    private QwenLawInterpretationRequest(
            LlmService llm,
            InstructionProvider instructionProvider,
            String text) {

        this.llm =
                llm;

        this.instructionProvider =
                instructionProvider;
        
        this.text =
                text;
    }
        
    public String text() {
        return text;
    }
    
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String instructions() {

        return instructionProvider.get(
                "qwen",
                "law_interpretation"
        );
    }

    @Override
    public String input() {
        return text;
    }

    @Override
    public Map<String, Object> format() {

        return null;
    }

    @Override
    public OllamaOptions options() {

        return new OllamaOptions(
                0.0,
                16384,  // mismo num_ctx que BatchEnrichment
                16,     // esto sí lo reducimos
                42
        );
    }

    @Override
    public Mono<Boolean> execute() {

        return llm.generate(this)
                .map(response -> {

	                boolean interpretation =
	                        Boolean.parseBoolean(
	                                response.trim()
	                        );
	                return interpretation;
	               }   
        );
    }

    public static class Builder
    implements LlmRequestBuilder<Boolean> {

		private InstructionProvider instructionProvider;
		
		private String text;
		
		private LlmService llm;

		public Builder instructionProvider(
		        InstructionProvider instructionProvider) {
		
		    this.instructionProvider =
		            instructionProvider;
		
		    return this;
		}
		
		@Override
		public Builder input(
		        Object input) {
		
		    if (input instanceof String text) {
		
		        this.text =
		                text;
		
		        return this;
		    }
		
		    throw new IllegalArgumentException(
		            "Input no soportado"
		                    + (input == null
		                            ? "null"
		                            : input.getClass().getName())
		    );
		}
		
		@Override
		public Builder llm(
		        LlmService llm) {
		
		    this.llm =
		            llm;
		
		    return this;
		}
		
		@Override
		public QwenLawInterpretationRequest build() {
		
		    return new QwenLawInterpretationRequest(
		            llm,
		            instructionProvider,
		            text
		    );
		}

			    }
}
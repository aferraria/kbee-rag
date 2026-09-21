package kbee.rag.qwen;

import java.util.Map;

import kbee.rag.config.InstructionProvider;
import kbee.rag.document.DocumentText;
import kbee.rag.llm.LlmRequestBuilder;
import kbee.rag.llm.LlmService;
import kbee.rag.ollama.OllamaLlmRequest;
import kbee.rag.search.DocumentAnalysisResponse;
import reactor.core.publisher.Mono;

public class QwenAnalysisRequest
        implements OllamaLlmRequest<DocumentAnalysisResponse> {

    private final String question;
    
    private final DocumentText document;

    private final LlmService llm;

    private final InstructionProvider instructionProvider;

    private QwenAnalysisRequest(
            LlmService llm,
            InstructionProvider instructionProvider,
            String question,
            DocumentText document) {

        this.llm =
                llm;

        this.instructionProvider =
                instructionProvider;
        
        this.question =
                question;

        this.document =
                document;
    }
        
    public String question() {
        return question;
    }
    
    public DocumentText document() {
        return document;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String instructions() {

        return instructionProvider.get(
                "qwen",
                "document_analysis"
        );
    }

    @Override
    public String input() {

        return buildInput();
    }

    @Override
    public Map<String, Object> format() {

        return null;
    }


    @Override
    public Mono<DocumentAnalysisResponse> execute() {

        return llm.generate(this)
                .map(answer ->
                	new DocumentAnalysisResponse(
                        document.documentId(),
                        document.documentTitle(),
                        answer
                )
        );
    }

    private String buildInput() {

        return """
                Pregunta del usuario:

                %s

                Documento a analizar:

                %s
                """.formatted(
                        question,
                        document.text()
                );
    }

   

    public static class Builder
    implements LlmRequestBuilder<DocumentAnalysisResponse> {

private InstructionProvider instructionProvider;

private String question;

private DocumentText document;

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
		
		    if (input instanceof String question) {
		
		        this.question =
		                question;
		
		        return this;
		    }
		
		    if (input instanceof DocumentText document) {
		
		        this.document =
		                document;
		
		        return this;
		    }
		
		    throw new IllegalArgumentException(
		            "Input no soportado por QwenAnalysisRequest: "
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
		public QwenAnalysisRequest build() {
		
		    return new QwenAnalysisRequest(
		            llm,
		            instructionProvider,
		            question,
		            document
		    );
		}

			    }
}
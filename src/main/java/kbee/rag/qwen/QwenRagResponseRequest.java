package kbee.rag.qwen;

import java.util.List;
import java.util.Map;


import com.fasterxml.jackson.databind.ObjectMapper;

import kbee.rag.config.InstructionProvider;
import kbee.rag.llm.LlmRequestBuilder;
import kbee.rag.llm.LlmResponseRequest;
import kbee.rag.llm.LlmService;
import kbee.rag.ollama.OllamaLlmRequest;
import kbee.rag.search.ExpandedSource;
import kbee.rag.search.RagResponse;
import kbee.rag.search.SegmentSearchResult;
import kbee.rag.search.Source;
import reactor.core.publisher.Mono;

public class QwenRagResponseRequest
        implements OllamaLlmRequest<RagResponse>,
        LlmResponseRequest {

    private final String question;

    private final LlmService llm;
    
    private final ObjectMapper objectMapper;

    private final InstructionProvider instructionProvider;

    private final List<ExpandedSource> sources;

    private QwenRagResponseRequest(
            LlmService llm,
            InstructionProvider instructionProvider,
            ObjectMapper objectMapper,
            String question,
            List<ExpandedSource> sources) {

        this.llm =
                llm;

        this.instructionProvider =
                instructionProvider;

        this.question =
                question;
        
        this.objectMapper =
        		objectMapper;

        this.sources =
                sources == null
                        ? List.of()
                        : List.copyOf(sources);
    }

    public String question() {

        return question;
    }

    public List<ExpandedSource> sources() {

        return sources;
    }

    public static Builder builder() {

        return new Builder();
    }

    @Override
    public String instructions() {

        return instructionProvider.get(
                "qwen",
                "rag_response"
        );
    }

    @Override
    public String input() {

        return buildInput();
    }

    	@Override
    	public Map<String, Object> format() {

    	    return Map.of(
    	            "type", "object",
    	            "properties", Map.of(
    	                    "ranking", Map.of(
    	                            "type", "array",
    	                            "items", Map.of(
    	                                    "type", "object",
    	                                    "properties", Map.of(
    	                                            "index", Map.of(
    	                                                    "type", "integer"
    	                                            )
    	                                    ),
    	                                    "required", List.of(
    	                                            "index"
    	                                    )
    	                            )
    	                    )
    	            ),
    	            "required", List.of(
    	                    "ranking"
    	            )
    	    );
    	}



    	@Override
    	public Mono<RagResponse> execute() {

    	    return llm.generate(this)
    	            .map(answer -> {

    	                List<ExpandedSource> selectedSources =
    	                        parseResponse(answer);

    	                List<Source> responseSources =
    	                        selectedSources.stream()
    	                                .map(this::toSource)
    	                                .toList();

    	                return new RagResponse(
    	                        question,
    	                        answer,
    	                        responseSources
    	                );
    	            });
    	}

    private String buildInput() {

        StringBuilder input =
                new StringBuilder();

        input.append(
                "PREGUNTA DEL USUARIO\n\n"
        );

        input.append(
                question
        );

        input.append(
                "\n\nFUENTES\n\n"
        );

        int sourceNumber = 1;

        for (ExpandedSource source :
                sources) {

            appendSource(
                    input,
                    sourceNumber,
                    source
            );

            sourceNumber++;
        }

        return input.toString();
    }

    private void appendSource(
            StringBuilder input,
            int sourceNumber,
            ExpandedSource source) {

        SegmentSearchResult selected =
                source.selected();

        input.append(
                "===== FUENTE "
                        + sourceNumber
                        + " =====\n"
        );

        if (selected.documentTitle() != null
                && !selected.documentTitle().isBlank()) {

            input.append(
                    "Título: "
                            + selected.documentTitle()
                            + "\n"
            );
        }

        if (selected.documentDate() != null) {

            input.append(
                    "Fecha: "
                            + selected.documentDate()
                            + "\n"
            );
        }

        if (selected.sectionPath() != null
                && !selected.sectionPath().isBlank()) {

            input.append(
                    "Sección: "
                            + selected.sectionPath()
                            + "\n"
            );
        }

        input.append("\n");

        if (selected.text() != null
                && !selected.text().isBlank()) {

            input.append(
                    selected.text()
            );

            input.append(
                    "\n\n"
            );
        }

        for (SegmentSearchResult segment :
                source.contextSegments()) {

            if (segment.text() != null
                    && !segment.text().isBlank()) {

                input.append(
                        segment.text()
                );

                input.append(
                        "\n\n"
                );
            }
        }
    }
    
    private Source toSource(
            ExpandedSource source) {

        SegmentSearchResult selected =
                source.selected();

        return new Source(
                selected.documentId(),
                selected.documentTitle(),
                selected.documentDate(),
                (float) selected.score()
        );
    }

    
    private List<ExpandedSource> parseResponse(
            String response) {

        try {

            QwenRerankResponse parsed =
                    objectMapper.readValue(
                            response,
                            QwenRerankResponse.class
                    );

            if (parsed.ranking() == null) {
                return List.of();
            }

            return parsed.ranking()
                    .stream()
                    .map(QwenRerankItem::index)
                    .filter(index ->
                            index >= 0
                            && index < sources.size()
                    )
                    .map(sources::get)
                    .toList();

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Respuesta inválida del LLM reranker: "
                            + response,
                    e
            );
        }
    }

    
		    public static class Builder
		    implements LlmRequestBuilder<RagResponse> {
		
		private InstructionProvider instructionProvider;
		
		private String question;
		
        private ObjectMapper objectMapper;

		
		private LlmService llm;
		
		private List<ExpandedSource> sources;
		
		public Builder instructionProvider(
		        InstructionProvider instructionProvider) {
		
		    this.instructionProvider =
		            instructionProvider;
		
		    return this;
		}
		
        public Builder objectMapper(
                ObjectMapper objectMapper) {

            this.objectMapper =
                    objectMapper;

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
		
		    if (input instanceof List<?> list) {
		
		        this.sources =
		                list.stream()
		                        .map(
		                                ExpandedSource.class::cast
		                        )
		                        .toList();
		
		        return this;
		    }
		
		    throw new IllegalArgumentException(
		            "Input no soportado por QwenRagResponseRequest: "
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
		public QwenRagResponseRequest build() {
		
		    return new QwenRagResponseRequest(
		            llm,
		            instructionProvider,
		            objectMapper,
		            question,
		            sources
		    );
		}
		}
		    
		    public record QwenRerankItem(
		            int index
		    ) {
		    }
		    public record QwenRerankResponse(
		            List<QwenRerankItem> ranking
		    ) {
		    }
}
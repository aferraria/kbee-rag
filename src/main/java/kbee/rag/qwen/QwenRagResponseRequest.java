package kbee.rag.qwen;

import java.util.List;
import java.util.Map;

import kbee.rag.config.InstructionProvider;
import kbee.rag.llm.LlmRequestBuilder;
import kbee.rag.llm.LlmService;
import kbee.rag.ollama.OllamaLlmRequest;
import kbee.rag.search.ExpandedSource;
import kbee.rag.search.RagResponse;
import kbee.rag.search.SegmentSearchResult;
import reactor.core.publisher.Mono;

public class QwenRagResponseRequest
        implements OllamaLlmRequest<RagResponse> {

    private final String question;

    private final LlmService llm;

    private final InstructionProvider instructionProvider;

    private final List<ExpandedSource> sources;

    private QwenRagResponseRequest(
            LlmService llm,
            InstructionProvider instructionProvider,
            String question,
            List<ExpandedSource> sources) {

        this.llm =
                llm;

        this.instructionProvider =
                instructionProvider;

        this.question =
                question;

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

        return null;
    }



    @Override
    public Mono<RagResponse> execute() {

        return llm.generate(this)
                .map(answer ->
                        new RagResponse(
                                question,
                                answer,
                                sources
                        )
                );
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


    
		    public static class Builder
		    implements LlmRequestBuilder<RagResponse> {
		
		private InstructionProvider instructionProvider;
		
		private String question;
		
		private LlmService llm;
		
		private List<ExpandedSource> sources;
		
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
		            question,
		            sources
		    );
		}
		}
}
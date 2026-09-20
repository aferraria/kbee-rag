package kbee.rag.qwen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kbee.rag.config.InstructionProvider;
import kbee.rag.llm.LlmEnrichmentRequest;
import kbee.rag.llm.LlmRequestBuilder;
import kbee.rag.llm.LlmService;
import kbee.rag.text.TextEnhanced;
import kbee.rag.text.TextEvaluation;
import kbee.rag.thesaurus.Concept;
import reactor.core.publisher.Mono;

public class QwenEnrichmentRequest
        extends AbstractQwenEnrichmentRequest<TextEnhanced>
        implements LlmEnrichmentRequest {

    private static final int MAX_PROPOSITIONS = 5;

    private final LlmService llm;

    private final InstructionProvider instructionProvider;

    private final ObjectMapper objectMapper;

    private final String text;

    private final List<Concept> voices;

    private QwenEnrichmentRequest(
            LlmService llm,
            InstructionProvider instructionProvider,
            ObjectMapper objectMapper,
            String text,
            List<Concept> voices) {

        this.llm =
                Objects.requireNonNull(
                        llm,
                        "llm"
                );

        this.instructionProvider =
                Objects.requireNonNull(
                        instructionProvider,
                        "instructionProvider"
                );

        this.objectMapper =
                Objects.requireNonNull(
                        objectMapper,
                        "objectMapper"
                );

        this.text =
                Objects.requireNonNull(
                        text,
                        "text"
                );

        this.voices =
                voices == null
                        ? List.of()
                        : List.copyOf(voices);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String text() {
        return text;
    }

    public List<Concept> voices() {
        return voices;
    }

    @Override
    public String instructions() {

        return instructionProvider.get(
                "qwen",
                "text_enrichment"
        );
    }

    @Override
    public String input() {
        return buildInput();
    }

    @Override
    public Map<String, Object> format() {
        return buildFormat();
    }

    @Override
    public Mono<TextEnhanced> execute() {

        return llm.generate(this)
                .map(response -> {

                    TextEvaluation evaluation =
                            parseTermEvaluation(
                                    response
                            );

                    List<Concept> selectedVoices =
                            reconstructVoices(
                                    voices,
                                    evaluation.voices()
                            );

                    List<String> propositions =
                            normalizePropositions(
                                    evaluation.propositions()
                            );

                    return new TextEnhanced(
                            text,
                            selectedVoices,
                            propositions
                    );
                })
		        .onErrorMap(
		                exception ->
		                        new IllegalStateException(
		                                "Error al ejecutar el enriquecimiento del texto",
		                                exception
		                        )
		        );
    }


    private String buildInput() {

        List<String> llmCandidates =
                buildLlmCandidates(
                        voices
                );

        return buildTextData(
                text,
                llmCandidates
        );
    }

    private String buildTextData(
            String text,
            List<String> candidateConcepts) {

        String voicesText =
                candidateConcepts == null
                        || candidateConcepts.isEmpty()
                        ? "(sin voces)"
                        : String.join(
                                "\n",
                                candidateConcepts
                        );

        return """

                === TEXTO ===

                %s

                === VOCES CANDIDATAS ===

                %s

                """.formatted(
                text,
                voicesText
        );
    }

    private Map<String, Object> buildFormat() {

        int maxVoices =
                (int) voices.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .count();

        Map<String, Object> voicesSchema =
                new LinkedHashMap<>();

        voicesSchema.put(
                "type",
                "array"
        );

        voicesSchema.put(
                "items",
                Map.of(
                        "type",
                        "string"
                )
        );

        voicesSchema.put(
                "maxItems",
                maxVoices
        );

        Map<String, Object> propositionsSchema =
                new LinkedHashMap<>();

        propositionsSchema.put(
                "type",
                "array"
        );

        propositionsSchema.put(
                "items",
                Map.of(
                        "type",
                        "string"
                )
        );

        propositionsSchema.put(
                "maxItems",
                MAX_PROPOSITIONS
        );

        Map<String, Object> properties =
                new LinkedHashMap<>();

        properties.put(
                "voices",
                voicesSchema
        );

        properties.put(
                "propositions",
                propositionsSchema
        );

        Map<String, Object> schema =
                new LinkedHashMap<>();

        schema.put(
                "type",
                "object"
        );

        schema.put(
                "properties",
                properties
        );

        schema.put(
                "required",
                List.of(
                        "voices",
                        "propositions"
                )
        );

        schema.put(
                "additionalProperties",
                false
        );

        return schema;
    }

    private TextEvaluation parseTermEvaluation(
            String response) {

        if (response == null
                || response.isBlank()) {

            return new TextEvaluation(
                    List.of(),
                    List.of()
            );
        }

        String json =
                response.trim();

        try {

            return objectMapper.readValue(
                    json,
                    TextEvaluation.class
            );

        } catch (JsonProcessingException firstException) {

            System.err.println(
                    "===== INVALID LLM JSON ====="
            );

            System.err.println(
                    "length = "
                            + json.length()
            );

            System.err.println(
                    "tail = "
                            + json.substring(
                                    Math.max(
                                            0,
                                            json.length() - 1000
                                    )
                            )
            );

            String repaired =
                    repairJson(
                            json
                    );

            try {

                return objectMapper.readValue(
                        repaired,
                        TextEvaluation.class
                );

            } catch (JsonProcessingException secondException) {

                secondException.addSuppressed(
                        firstException
                );

                throw new IllegalStateException(
                        "No se pudo interpretar "
                                + "la respuesta del LLM: "
                                + response,
                        secondException
                );
            }
        }
    }

    public static class Builder
            implements LlmRequestBuilder<TextEnhanced> {

        private InstructionProvider instructionProvider;

        private ObjectMapper objectMapper;

        private String text;

        private LlmService llm;

        private List<Concept> voices;

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

            if (input instanceof String text) {

                this.text =
                        text;

                return this;
            }

            if (input instanceof List<?> list) {

                this.voices =
                        list.stream()
                                .map(
                                        Concept.class::cast
                                )
                                .toList();

                return this;
            }

            throw new IllegalArgumentException(
                    "Input no soportado por QwenEnrichmentRequest: "
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
        public QwenEnrichmentRequest build() {

            return new QwenEnrichmentRequest(
                    llm,
                    instructionProvider,
                    objectMapper,
                    text,
                    voices
            );
        }
    }
}
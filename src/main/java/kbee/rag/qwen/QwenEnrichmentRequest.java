package kbee.rag.qwen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
                    
                    List<String> normalizedVoices =
                            normalizeSelectedTerms(
                                    evaluation.voices()
                            );

                    List<Concept> selectedVoices =
                            reconstructVoices(
                            //reconstructTextVoices(
                                    voices,
                                    normalizedVoices
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
    
    private static final double AMBIGUOUS_VOICE_FACTOR = 0.90;

    protected List<Concept> reconstructTextVoices(
            List<Concept> candidateVoices,
            List<String> selectedTerms) {

        if (candidateVoices == null
                || candidateVoices.isEmpty()
                || selectedTerms == null
                || selectedTerms.isEmpty()) {

            return List.of();
        }

        Set<String> selectedTermSet =
                selectedTerms.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(term -> !term.isBlank())
                        .collect(Collectors.toCollection(
                                LinkedHashSet::new
                        ));

        List<Concept> result =
                new ArrayList<>();

        Set<String> consumedTerms =
                new HashSet<>();

        /*
         * =================================================
         * 1. VOCES COMPLETAS
         * =================================================
         *
         * Conservamos:
         *
         * - voces completas seleccionadas directamente;
         * - voces reconstruidas cuando todos sus
         *   componentes fueron seleccionados.
         */

        for (Concept concept : candidateVoices) {

            if (concept == null
                    || concept.term() == null
                    || concept.term().isBlank()) {

                continue;
            }

            String voice =
                    concept.term().trim();

            /*
             * Voz completa seleccionada directamente
             * por el LLM.
             */
            if (selectedTermSet.contains(voice)) {

                result.add(concept);

                Arrays.stream(
                                voice.split("\\s*>\\s*")
                        )
                        .map(String::trim)
                        .filter(term -> !term.isBlank())
                        .forEach(consumedTerms::add);

                continue;
            }

            /*
             * Intentamos reconstruir la voz completa
             * a partir de sus componentes.
             *
             * DERECHO es una raíz genérica y no necesita
             * haber sido seleccionada.
             */
            List<String> components =
                    Arrays.stream(
                                    voice.split("\\s*>\\s*")
                            )
                            .map(String::trim)
                            .filter(term -> !term.isBlank())
                            .filter(term ->
                                    !term.equals("DERECHO")
                            )
                            .toList();

            if (!components.isEmpty()
                    && components.stream()
                            .allMatch(selectedTermSet::contains)) {

                result.add(concept);
                consumedTerms.addAll(components);
            }
        }

        /*
         * =================================================
         * 2. EXPANSION POR HOJA
         * =================================================
         *
         * IMPORTANTE:
         *
         * Aunque un término haya sido consumido para
         * reconstruir una voz completa, igualmente
         * buscamos otras voces que tengan ese término
         * como hoja.
         *
         * Ejemplo:
         *
         * seleccionados:
         *
         * PRUEBA
         * NEGLIGENCIA PROBATORIA
         *
         * Paso 1:
         *
         * PRUEBA > NEGLIGENCIA PROBATORIA
         *
         * Paso 2:
         *
         * también podemos recuperar:
         *
         * PRUEBA > PRODUCCION > NEGLIGENCIA PROBATORIA
         * ...
         */

        for (String selectedTerm : selectedTermSet) {

            /*
             * Buscamos todas las voces donde selectedTerm
             * sea exactamente la hoja.
             */
            Map<String, Concept> matchingVoices =
                    candidateVoices.stream()
                            .filter(Objects::nonNull)
                            .filter(concept ->
                                    concept.term() != null
                                            && !concept.term().isBlank()
                            )
                            .filter(concept -> {

                                List<String> components =
                                        Arrays.stream(
                                                        concept.term()
                                                                .split("\\s*>\\s*")
                                                )
                                                .map(String::trim)
                                                .filter(term ->
                                                        !term.isBlank()
                                                )
                                                .toList();

                                if (components.isEmpty()) {
                                    return false;
                                }

                                String leaf =
                                        components.get(
                                                components.size() - 1
                                        );

                                return selectedTerm.equals(leaf);
                            })
                            .collect(Collectors.toMap(
                                    concept ->
                                            concept.term().trim(),

                                    Function.identity(),

                                    /*
                                     * Si la misma voz aparece varias veces,
                                     * conservamos la de mayor score.
                                     */
                                    (a, b) ->
                                            a.score() >= b.score()
                                                    ? a
                                                    : b,

                                    LinkedHashMap::new
                            ));

            /*
             * Agregamos las reconstrucciones que todavía
             * no estén presentes en result.
             */
            for (Concept concept : matchingVoices.values()) {

                String voice =
                        concept.term().trim();

                boolean alreadyPresent =
                        result.stream()
                                .filter(Objects::nonNull)
                                .filter(existing ->
                                        existing.term() != null
                                )
                                .anyMatch(existing ->
                                        voice.equals(
                                                existing.term().trim()
                                        )
                                );

                if (!alreadyPresent) {

                    /*
                     * Esta voz se obtiene por expansión
                     * de una hoja y no por reconstrucción
                     * completa.
                     *
                     * Por ahora mantenemos el factor que
                     * ya veníamos utilizando.
                     */
                    result.add(
                            new Concept(
                                    voice,
                                    (float) (
                                            concept.score()
                                                    * AMBIGUOUS_VOICE_FACTOR
                                    )
                            )
                    );
                }
            }

            /*
             * Si selectedTerm ya participó en una voz
             * completa, NO lo agregamos además como
             * término independiente.
             *
             * Ojo: este control está DESPUÉS de la
             * expansión por hoja.
             */
            if (consumedTerms.contains(selectedTerm)) {
                continue;
            }

            /*
             * Si encontramos voces cuya hoja coincide,
             * tampoco necesitamos conservar el término
             * suelto.
             */
            if (!matchingVoices.isEmpty()) {
                continue;
            }

            /*
             * No existe ninguna voz cuya hoja sea
             * selectedTerm.
             *
             * Conservamos el comportamiento anterior:
             * buscamos el mejor candidato que contenga
             * el término y lo mantenemos individualmente.
             */
            candidateVoices.stream()
                    .filter(Objects::nonNull)
                    .filter(concept ->
                            concept.term() != null
                    )
                    .filter(concept ->
                            Arrays.stream(
                                            concept.term()
                                                    .split("\\s*>\\s*")
                                    )
                                    .map(String::trim)
                                    .anyMatch(
                                            selectedTerm::equals
                                    )
                    )
                    .max(Comparator.comparingDouble(
                            Concept::score
                    ))
                    .ifPresent(concept -> {

                        boolean alreadyPresent =
                                result.stream()
                                        .filter(Objects::nonNull)
                                        .filter(existing ->
                                                existing.term() != null
                                        )
                                        .anyMatch(existing ->
                                                selectedTerm.equals(
                                                        existing.term().trim()
                                                )
                                        );

                        if (!alreadyPresent) {
                            result.add(
                                    new Concept(
                                            selectedTerm,
                                            concept.score()
                                    )
                            );
                        }
                    });
        }

        return result;
    }
    
    
    private List<String> normalizeSelectedTerms(
            List<String> selectedTerms) {

        if (selectedTerms == null
                || selectedTerms.isEmpty()) {
            return List.of();
        }

        return selectedTerms.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(term -> !term.isBlank())
                .map(term -> {

                    String[] components =
                            term.split("\\s*>\\s*");

                    return components[
                            components.length - 1
                    ].trim();
                })
                .distinct()
                .toList();
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
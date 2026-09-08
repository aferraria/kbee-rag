package kbee.rag.text;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import kbee.rag.config.InstructionProvider;
import kbee.rag.llm.LlmRequest;
import kbee.rag.llm.LlmService;
import kbee.rag.search.Concept;
import kbee.rag.search.ConceptExtraction;
import kbee.rag.search.ThesaursService;
import reactor.core.publisher.Mono;

@Service
public class DefaultLegalTextEnhancer
        implements LegalTextEnhancer {

    private final LlmService llmService;

    private final ThesaursService conceptExtractorService;

    private final ObjectMapper objectMapper;

    private final InstructionProvider instructionProvider;

    public DefaultLegalTextEnhancer(
            LlmService llmService,
            ThesaursService conceptExtractorService,
            ObjectMapper objectMapper,
            InstructionProvider instructionProvider) {

        this.llmService =
                llmService;

        this.conceptExtractorService =
                conceptExtractorService;

        this.objectMapper =
                objectMapper;

        this.instructionProvider =
                instructionProvider;
    }

    @Override
    public Mono<LegalEnhancement> enhance(
            String text,
            String promptName) {

        if (text == null
                || text.isBlank()) {

            return Mono.just(
                    new LegalEnhancement(
                            "",
                            List.of(),
                            List.of()
                    )
            );
        }

        if (promptName == null
                || promptName.isBlank()) {

            return Mono.error(
                    new IllegalArgumentException(
                            "promptName must not be null or blank"
                    )
            );
        }

        return conceptExtractorService
                .candidates(
                        text
                )

                .defaultIfEmpty(
                        new ConceptExtraction(
                                List.of()
                        )
                )

                .flatMap(extraction -> {

                    List<Concept> candidateVoices =
                            extraction.concepts() == null
                                    ? List.of()
                                    : extraction.concepts()
                                            .stream()
                                            .filter(
                                                    Objects::nonNull
                                            )
                                            .filter(concept ->
                                                    concept.term() != null
                                                            && !concept.term()
                                                                    .isBlank()
                                            )
                                            .toList();

                    /*
                     * No mandamos una cantidad ilimitada
                     * de voces al LLM.
                     */
                    List<String> llmCandidates =
                            candidateVoices.stream()
                                    .limit(
                                            60
                                    )
                                    .map(
                                            Concept::term
                                    )
                                    .toList();

                    String data =
                            buildTextData(
                                    text,
                                    llmCandidates
                            );

                    String instructions =
                            instructionProvider.get(
                                    promptName
                            );

                    LlmRequest request =
                            new LlmRequest(
                                    instructions,
                                    data,
                                    buildEnrichmentFormat(llmCandidates)
                            );

                    return llmService
                            .generate(
                                    request
                            )
                            .map(response -> {
                            	
                            	TermEvaluation evaluation =
                            	        parseTermEvaluation(
                            	                response
                            	        );

                            	List<TermDecision> selectedTerms =
                            	        distinctTermDecisions(
                            	                evaluation.terminos()
                            	        );

                            	List<Concept> voices =
                            	        rebuildSupportedVoices(
                            	                candidateVoices,
                            	                selectedTerms
                            	        );

                                List<String> propositions =
                                        evaluation.propositions() == null
                                                ? List.of()
                                                : evaluation
                                                        .propositions()
                                                        .stream()
                                                        .filter(
                                                                Objects::nonNull
                                                        )
                                                        .map(
                                                                String::trim
                                                        )
                                                        .filter(value ->
                                                                !value.isBlank()
                                                        )
                                                        .distinct()
                                                        .toList();

                                String legalText =
                                        formatLegalText(
                                                voices,
                                                propositions
                                        );

                                return new LegalEnhancement(
                                        legalText,
                                        voices,
                                        propositions
                                );
                            });
                });
    }

    /*
     * =================================================
     * RESPUESTA LLM
     * =================================================
     */

    private TermEvaluation parseTermEvaluation(
            String response) {

        if (response == null
                || response.isBlank()) {

            return new TermEvaluation(
                    List.of(),
                    List.of()
            );
        }

        String json =
                response.trim();

        try {

            return objectMapper.readValue(
                    json,
                    TermEvaluation.class
            );

        } catch (Exception firstException) {
        	
 
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
                        TermEvaluation.class
                );

            } catch (Exception secondException) {

                throw new IllegalStateException(
                        "No se pudo interpretar "
                                + "la respuesta del LLM: "
                                + response,
                        secondException
                );
            }
        }
    }

    /*
     * =================================================
     * RECONSTRUCCIÓN DE VOCES
     * =================================================
     */

    private List<Concept> rebuildSupportedVoices(
            List<Concept> candidateVoices,
            List<TermDecision> selectedTerms) {

        if (candidateVoices == null
                || candidateVoices.isEmpty()
                || selectedTerms == null
                || selectedTerms.isEmpty()) {

            return List.of();
        }

        Set<String> supportedTerms =
                selectedTerms.stream()
                        .filter(
                                Objects::nonNull
                        )
                        .filter(
                                this::isSelected
                        )
                        .map(
                                TermDecision::termino
                        )
                        .filter(
                                Objects::nonNull
                        )
                        .map(
                                String::trim
                        )
                        .filter(term ->
                                !term.isBlank()
                        )
                        .collect(
                                Collectors.toSet()
                        );

        return candidateVoices.stream()
                .filter(
                        Objects::nonNull
                )
                .filter(concept ->
                        concept.term() != null
                                && !concept.term()
                                        .isBlank()
                )
                .filter(concept ->
                        Arrays.stream(
                                concept.term()
                                        .split(">")
                        )
                        .map(
                                String::trim
                        )
                        .filter(term ->
                                !term.isBlank()
                        )
                        .allMatch(
                                supportedTerms::contains
                        )
                )
                .toList();
    }
    
    private List<TermDecision> distinctTermDecisions(
            List<TermDecision> decisions) {

        if (decisions == null
                || decisions.isEmpty()) {
            return List.of();
        }

        Map<String, TermDecision> unique =
                new LinkedHashMap<>();

        for (TermDecision decision : decisions) {

            if (decision == null
                    || decision.termino() == null
                    || decision.termino().isBlank()) {
                continue;
            }

            String term =
                    decision.termino()
                            .trim();

            unique.putIfAbsent(
                    term,
                    new TermDecision(
                            term,
                            decision.justificacion()
                    )
            );
        }

        return List.copyOf(
                unique.values()
        );
    }

    /*
     * Protección adicional.
     *
     * El prompt actual no debería devolver FALSE,
     * pero mantenemos esta validación por robustez.
     */
    private boolean isSelected(
            TermDecision decision) {

        if (decision == null) {
            return false;
        }

        String justification =
                decision.justificacion();

        if (justification == null
                || justification.isBlank()) {

            return true;
        }

        String normalized =
                justification
                        .trim()
                        .toUpperCase();

        return !normalized.contains(
                "FALSE"
        );
    }

    /*
     * =================================================
     * REPARACIÓN JSON
     * =================================================
     */

    private String repairJson(
            String json) {

        return json
                .replaceAll(
                        "\"\\s*\\\\.\\s*,",
                        ".\","
                )
                .replaceAll(
                        "\"\\s*\\\\.\\s*]",
                        ".\"]"
                );
    }

    /*
     * =================================================
     * TEXTO LEGAL PARA EMBEDDING
     * =================================================
     */

    private String formatLegalText(
            List<Concept> voices,
            List<String> propositions) {

        StringBuilder result =
                new StringBuilder();

        if (voices != null
                && !voices.isEmpty()) {

            result.append(
                    "VOCES JURÍDICAS:\n"
            );

            for (Concept concept :
                    voices) {

                if (concept == null
                        || concept.term() == null
                        || concept.term().isBlank()) {

                    continue;
                }

                result.append(
                        concept.term().trim()
                )
                .append(
                        '\n'
                );
            }
        }

        if (propositions != null
                && !propositions.isEmpty()) {

            if (!result.isEmpty()) {

                result.append(
                        '\n'
                );
            }

            result.append(
                    "PROPOSICIONES JURÍDICAS:\n"
            );

            for (String proposition :
                    propositions) {

                if (proposition == null
                        || proposition.isBlank()) {

                    continue;
                }

                result.append(
                        proposition.trim()
                )
                .append(
                        '\n'
                );
            }
        }

        return result
                .toString()
                .trim();
    }

    /*
     * =================================================
     * JSON SCHEMA
     * =================================================
     */

    private Map<String, Object> buildEnrichmentFormat(
            List<String> candidateConcepts) {

        int maxTerms =
                candidateConcepts == null
                        ? 0
                        : candidateConcepts.stream()
                                .filter(Objects::nonNull)
                                .flatMap(voice ->
                                        Arrays.stream(
                                                voice.split(">")
                                        )
                                )
                                .map(String::trim)
                                .filter(term ->
                                        !term.isBlank()
                                )
                                .distinct()
                                .toList()
                                .size();

        Map<String, Object> termProperties =
                new LinkedHashMap<>();

        termProperties.put(
                "termino",
                Map.of(
                        "type",
                        "string"
                )
        );

        termProperties.put(
                "justificacion",
                Map.of(
                        "type",
                        "string"
                )
        );

        Map<String, Object> termSchema =
                new LinkedHashMap<>();

        termSchema.put(
                "type",
                "object"
        );

        termSchema.put(
                "properties",
                termProperties
        );

        termSchema.put(
                "required",
                List.of(
                        "termino",
                        "justificacion"
                )
        );

        termSchema.put(
                "additionalProperties",
                false
        );

        Map<String, Object> termsSchema =
                new LinkedHashMap<>();

        termsSchema.put(
                "type",
                "array"
        );

        termsSchema.put(
                "items",
                termSchema
        );

        termsSchema.put(
                "maxItems",
                maxTerms
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
                20
        );

        Map<String, Object> properties =
                new LinkedHashMap<>();

        properties.put(
                "terminos",
                termsSchema
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
                        "terminos",
                        "propositions"
                )
        );

        schema.put(
                "additionalProperties",
                false
        );

        return schema;
    }

    /*
     * =================================================
     * DATOS PARA EL PROMPT
     * =================================================
     */

    private String buildTextData(
            String text,
            List<String> candidateConcepts) {

        List<String> terms =
                candidateConcepts == null
                        ? List.of()
                        : candidateConcepts.stream()

                                .filter(
                                        Objects::nonNull
                                )

                                /*
                                 * Una voz jerárquica:
                                 *
                                 * SENTENCIA > ARBITRARIEDAD
                                 *
                                 * se envía al LLM como:
                                 *
                                 * SENTENCIA
                                 * ARBITRARIEDAD
                                 */
                                .flatMap(voice ->
                                        Arrays.stream(
                                                voice.split(">")
                                        )
                                )

                                .map(
                                        String::trim
                                )

                                .filter(term ->
                                        !term.isBlank()
                                )

                                .distinct()

                                .toList();

        String termsText =
                terms.isEmpty()
                        ? "(sin términos)"
                        : String.join(
                                "\n",
                                terms
                        );

        return """
            === TEXTO ===

            %s

            === TERMINOS ===

            %s
            """.formatted(
                text,
                termsText
        );
    }

    /*
     * =================================================
     * DTO INTERNOS
     * =================================================
     */

    private record TermDecision(
            String termino,
            String justificacion
    ) {
    }

    private record TermEvaluation(
            List<TermDecision> terminos,
            List<String> propositions
    ) {
    }
}
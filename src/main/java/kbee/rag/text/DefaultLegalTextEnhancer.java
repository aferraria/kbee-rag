package kbee.rag.text;

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
import java.util.regex.Pattern;
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

import java.util.stream.Stream;

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
                .candidates(text)
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
                                            .filter(Objects::nonNull)
                                            .filter(concept ->
                                                    concept.term() != null
                                                            && !concept.term().isBlank()
                                            )
                                            .toList();

                    List<String> llmCandidates =
                            candidateVoices.stream()
                                    .map(Concept::term)
                                    .filter(Objects::nonNull)
                                    .map(String::trim)
                                    .filter(term -> !term.isBlank())
                                    .flatMap(voice -> {

                                        Stream<String> fullVoice =
                                                Stream.of(voice);

                                        Stream<String> atomicTerms =
                                                Arrays.stream(
                                                                voice.split("\\s*>\\s*")
                                                        )
                                                        .map(String::trim)
                                                        .filter(term -> !term.isBlank())
                                                        .filter(term ->
                                                                !term.equals("DERECHO")
                                                        );

                                        return Stream.concat(
                                                fullVoice,
                                                atomicTerms
                                        );
                                    })
                                    .distinct()
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
                                    buildEnrichmentFormat(
                                            llmCandidates
                                    )
                            );

                    return llmService
                            .generate(request)
                            .flatMap(response -> {

                                TermEvaluation evaluation =
                                        parseTermEvaluation(response);

                                List<Concept> voices =
                                        reconstructVoices(
                                                candidateVoices,
                                                evaluation.terminos()
                                        );

                                voices = expand(voices);

                                List<String> propositions =
                                        evaluation.propositions() == null
                                                ? List.of()
                                                : evaluation.propositions()
                                                        .stream()
                                                        .filter(Objects::nonNull)
                                                        .map(String::trim)
                                                        .filter(value ->
                                                                !value.isBlank()
                                                        )
                                                        .distinct()
                                                        .toList();

                                return enrichLawInterpretation(
                                        text,
                                        voices
                                )
                                .map(finalVoices -> {

                                    String legalText =
                                            formatLegalText(
                                                    finalVoices,
                                                    propositions
                                            );

                                    return new LegalEnhancement(
                                            legalText,
                                            finalVoices,
                                            propositions
                                    );
                                });
                            });
                });
    }
    
    @Override
    public Mono<List<LegalEnhancement>> enhance(
            List<String> texts,
            String promptName) {

        if (texts == null
                || texts.isEmpty()) {

            return Mono.just(
                    List.of()
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

        /*
         * Creamos los segmentos del batch manteniendo
         * la posición original.
         *
         * Los IDs enviados al LLM son 1..N.
         */
        List<BatchInput> inputs =
                java.util.stream.IntStream
                        .range(
                                0,
                                texts.size()
                        )
                        .mapToObj(index ->
                                new BatchInput(
                                        index + 1,
                                        texts.get(index)
                                )
                        )
                        .toList();

        /*
         * Obtenemos candidatos para cada segmento.
         */
        return reactor.core.publisher.Flux
                .fromIterable(
                        inputs
                )
                .concatMap(input -> {

                    String text =
                            input.text();

                    if (text == null
                            || text.isBlank()) {

                        return Mono.just(
                                new BatchCandidateInput(
                                        input.id(),
                                        "",
                                        List.of()
                                )
                        );
                    }

                    long startCandidates =
                            System.nanoTime();

                    return conceptExtractorService
                            .candidates(
                                    text
                            )
                            .doOnNext(result -> {

                                long elapsed =
                                        System.nanoTime()
                                                - startCandidates;

                            })
                            .defaultIfEmpty(
                                    new ConceptExtraction(
                                            List.of()
                                    )
                            )
                            .map(extraction -> {

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
                                                                        && !concept
                                                                                .term()
                                                                                .isBlank()
                                                        )
                                                        .limit(
                                                                40
                                                        )
                                                        .toList();

                                return new BatchCandidateInput(
                                        input.id(),
                                        text,
                                        candidateVoices
                                );
                            });
                })
                .collectList()

                /*
                 * Enrichment normal del batch.
                 */
                .flatMap(batchInputs ->
                        enhanceBatch(
                                batchInputs,
                                promptName,
                                texts.size()
                        )
                )

                /*
                 * Segundo paso:
                 *
                 * Para los segmentos que contienen una referencia
                 * normativa preguntamos al LLM si existe una
                 * interpretación de la norma.
                 *
                 * flatMapSequential permite ejecutar hasta 2
                 * verificaciones simultáneas manteniendo el orden
                 * original de los segmentos.
                 */
                .flatMap(enhancements ->
                        reactor.core.publisher.Flux
                                .range(
                                        0,
                                        enhancements.size()
                                )
                                .flatMapSequential(
                                        index -> {

                                            String text =
                                                    texts.get(
                                                            index
                                                    );

                                            LegalEnhancement enhancement =
                                                    enhancements.get(
                                                            index
                                                    );

                                            return enrichLawInterpretation(
                                                    text,
                                                    enhancement.concepts()
                                            )
                                            .map(finalVoices -> {

                                                String legalText =
                                                        formatLegalText(
                                                                finalVoices,
                                                                enhancement.propositions()
                                                        );

                                                return new LegalEnhancement(
                                                        legalText,
                                                        finalVoices,
                                                        enhancement.propositions()
                                                );
                                            });
                                        },
                                        2
                                )
                                .collectList()
                );
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

    
    private List<Concept> reconstructVoices(
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
         * 1. Voces completas seleccionadas directamente
         *    o reconstruidas a partir de sus componentes.
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
             * Voz completa seleccionada por el LLM.
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
             * Intentamos reconstruir la voz.
             *
             * DERECHO es raíz genérica y no necesita
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
         * 2. Conservamos los términos seleccionados
         *    que no pudieron formar parte de una voz.
         */
        for (String selectedTerm : selectedTermSet) {

            if (consumedTerms.contains(selectedTerm)) {
                continue;
            }

            /*
             * Si ya agregamos exactamente esa voz,
             * no la repetimos.
             */
            boolean alreadyPresent =
                    result.stream()
                            .anyMatch(concept ->
                                    selectedTerm.equals(
                                            concept.term().trim()
                                    )
                            );

            if (alreadyPresent) {
                continue;
            }

            /*
             * Recuperamos el score del mejor candidato
             * que contenga ese término como componente.
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
                    .ifPresent(concept ->
                            result.add(
                                    new Concept(
                                            selectedTerm,
                                            concept.score()
                                    )
                            )
                    );
        }

        return result;
    }
    /*
     * =================================================
     * RECONSTRUCCIÓN DE VOCES
     * =================================================
     */

//    private List<Concept> rebuildSupportedVoices(
//            List<Concept> candidateVoices,
//            List<TermDecision> selectedTerms) {
//
//        if (candidateVoices == null
//                || candidateVoices.isEmpty()
//                || selectedTerms == null
//                || selectedTerms.isEmpty()) {
//
//            return List.of();
//        }
//
//        Set<String> supportedTerms =
//                selectedTerms.stream()
//                        .filter(
//                                Objects::nonNull
//                        )
//                        .filter(
//                                this::isSelected
//                        )
//                        .map(
//                                TermDecision::termino
//                        )
//                        .filter(
//                                Objects::nonNull
//                        )
//                        .map(
//                                String::trim
//                        )
//                        .filter(term ->
//                                !term.isBlank()
//                        )
//                        .collect(
//                                Collectors.toSet()
//                        );
//
//        return candidateVoices.stream()
//                .filter(
//                        Objects::nonNull
//                )
//                .filter(concept ->
//                        concept.term() != null
//                                && !concept.term()
//                                        .isBlank()
//                )
//                .filter(concept ->
//                        Arrays.stream(
//                                concept.term()
//                                        .split(">")
//                        )
//                        .map(
//                                String::trim
//                        )
//                        .filter(term ->
//                                !term.isBlank()
//                        )
//                        .allMatch(
//                                supportedTerms::contains
//                        )
//                )
//                .toList();
//    }
    
//    private List<TermDecision> distinctTermDecisions(
//            List<TermDecision> decisions) {
//
//        if (decisions == null
//                || decisions.isEmpty()) {
//            return List.of();
//        }
//
//        Map<String, TermDecision> unique =
//                new LinkedHashMap<>();
//
//        for (TermDecision decision : decisions) {
//
//            if (decision == null
//                    || decision.termino() == null
//                    || decision.termino().isBlank()) {
//                continue;
//            }
//
//            String term =
//                    decision.termino()
//                            .trim();
//
//            unique.putIfAbsent(
//                    term,
//                    new TermDecision(
//                            term,
//                            decision.justificacion()
//                    )
//            );
//        }
//
//        return List.copyOf(
//                unique.values()
//        );
//    }

    /*
     * Protección adicional.
     *
     * El prompt actual no debería devolver FALSE,
     * pero mantenemos esta validación por robustez.
     */
//    private boolean isSelected(
//            TermDecision decision) {
//
//        if (decision == null) {
//            return false;
//        }
//
//        String justification =
//                decision.justificacion();
//
//        if (justification == null
//                || justification.isBlank()) {
//
//            return true;
//        }
//
//        String normalized =
//                justification
//                        .trim()
//                        .toUpperCase();
//
//        return !normalized.contains(
//                "FALSE"
//        );
//    }
    
    
    private Mono<List<Concept>> enrichLawInterpretation(
            String text,
            List<Concept> selectedVoices) {

        if (!containsLegalReference(text)) {
            return Mono.just(selectedVoices);
        }

        
        String instructions =
                instructionProvider.get(
                        "law-interpretation"
                );
        
//        String instructions2 =
//                """
//                Determina si el TEXTO realiza una interpretación jurídica
//                de una ley o de una disposición legal mencionada en él.
//
//                Existe interpretación cuando el texto determina, analiza
//                o discute el sentido, alcance o aplicabilidad de la norma.
//
//                La mera cita, mención o invocación de una ley o artículo
//                no constituye interpretación.
//
//                Responde únicamente true o false.
//                """;

        LlmRequest request =
                new LlmRequest(
                        instructions,
                        text,
                        Map.of(
                                "type", "boolean"
                        )
                );

        return llmService
                .generate(request)
                .map(response -> {

                    boolean interpretation =
                            Boolean.parseBoolean(
                                    response.trim()
                            );

                    if (!interpretation) {
                        return selectedVoices;
                    }

                    boolean alreadyPresent =
                            selectedVoices.stream()
                                    .anyMatch(concept ->
                                            "INTERPRETACION DE LA LEY"
                                                    .equals(concept.term())
                                    );

                    if (alreadyPresent) {
                        return selectedVoices;
                    }

                    List<Concept> result =
                            new ArrayList<>(selectedVoices);

                    result.add(
                            new Concept(
                                    "INTERPRETACION DE LA LEY",
                                    (float)1.0
                            )
                    );

                    return List.copyOf(result);
                });
    }
    
    private static final Pattern LEGAL_REFERENCE_PATTERN =
            Pattern.compile(
                    "\\b(?:"
                            + "ley(?:es)?"
                            + "|decreto(?:s)?"
                            + "|art(?:í|i)culo(?:s)?"
                            + "|art\\."
                            + ")\\b",
                    Pattern.CASE_INSENSITIVE
                            | Pattern.UNICODE_CASE
            );

    private boolean containsLegalReference(
            String text) {

        if (text == null || text.isBlank()) {
            return false;
        }

        return LEGAL_REFERENCE_PATTERN
                .matcher(text)
                .find();
    }

    
    /*
     * =================================================
     * EXPANSION POR RELACIONES
     *  =================================================
     */
    private List<Concept> expand(List<Concept> voices) {
    	List<Concept> expanded = new ArrayList<>();
    	for (Concept voice : voices) {
    		if (voice.term().equals("PRUEBA > NEGLIGENCIA PROBATORIA")) {
        		expanded.add(new Concept("PRUEBA > PRODUCCION > NEGLIGENCIA PROBATORIA", voice.score()));
    		}
    		expanded.add(voice);
    	}
    	return expanded;
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
    
    private Mono<List<LegalEnhancement>> enhanceBatch(
            List<BatchCandidateInput> inputs,
            String promptName,
            int originalSize) {

        boolean hasText =
                inputs.stream()
                        .anyMatch(input ->
                                input.text() != null
                                        && !input.text().isBlank()
                        );

        if (!hasText) {
            return Mono.just(
                    java.util.stream.IntStream
                            .range(0, originalSize)
                            .mapToObj(index ->
                                    emptyEnhancement()
                            )
                            .toList()
            );
        }

        return evaluateBatch(
                inputs,
                promptName
        )
        .flatMap(evaluationsById -> {

            List<BatchCandidateInput> missingInputs =
                    findMissingInputs(
                            inputs,
                            evaluationsById
                    );

            if (missingInputs.isEmpty()) {
                return Mono.just(
                        buildEnhancements(
                                inputs,
                                evaluationsById
                        )
                );
            }

            System.err.println(
                    "===== LLM BATCH INCOMPLETO ====="
            );

            System.err.println(
                    "Segmentos faltantes: "
                            + missingInputs.stream()
                                    .map(
                                            BatchCandidateInput::id
                                    )
                                    .toList()
            );

            System.err.println(
                    "Reintentando únicamente "
                            + "los segmentos faltantes..."
            );

            return evaluateBatch(
                    missingInputs,
                    promptName
            )
            .map(retryEvaluationsById -> {

                Map<Integer, BatchSegmentEvaluation> merged =
                        new LinkedHashMap<>(
                                evaluationsById
                        );

                retryEvaluationsById.forEach(
                        merged::putIfAbsent
                );

                List<BatchCandidateInput> stillMissing =
                        findMissingInputs(
                                inputs,
                                merged
                        );

                if (!stillMissing.isEmpty()) {

                    System.err.println(
                            "===== LLM BATCH INCOMPLETO "
                                    + "DESPUÉS DEL RETRY ====="
                    );

                    System.err.println(
                            "Segmentos todavía faltantes: "
                                    + stillMissing.stream()
                                            .map(
                                                    BatchCandidateInput::id
                                            )
                                            .toList()
                    );

                    System.err.println(
                            "Se continuará con "
                                    + "enhancement vacío para "
                                    + "esos segmentos."
                    );
                }

                return buildEnhancements(
                        inputs,
                        merged
                );
            });
        });
    }

    /*
     * Ejecuta una única evaluación batch del LLM.
     *
     * El resultado queda indexado por el ID enviado al modelo.
     * No se exige aquí que estén todos los IDs: esa validación
     * se realiza en enhanceBatch(), porque una omisión ocasional
     * del LLM se recupera reintentando solamente los faltantes.
     */
    private Mono<Map<Integer, BatchSegmentEvaluation>> evaluateBatch(
            List<BatchCandidateInput> inputs,
            String promptName) {

        /*
         * Para el LLM descomponemos las voces del tesauro.
         *
         * Ejemplo:
         *
         * PRUEBA > PRODUCCION > NEGLIGENCIA PROBATORIA
         *
         * se envía como:
         *
         * PRUEBA > PRODUCCION > NEGLIGENCIA PROBATORIA
         * PRUEBA
         * PRODUCCION
         * NEGLIGENCIA PROBATORIA
         *
         * Se excluye DERECHO como término atómico.
         *
         * Los candidateVoices originales de "inputs" no se modifican,
         * porque después son necesarios para reconstructVoices().
         */
        List<BatchCandidateInput> llmInputs =
                inputs.stream()
                        .map(input -> {

                            List<Concept> llmCandidates =
                                    input.candidateVoices() == null
                                            ? List.of()
                                            : input.candidateVoices()
                                                    .stream()
                                                    .filter(Objects::nonNull)
                                                    .filter(concept ->
                                                            concept.term() != null
                                                                    && !concept.term().isBlank()
                                                    )
                                                    .flatMap(concept -> {

                                                        String voice =
                                                                concept.term().trim();

                                                        Stream<Concept> fullVoice =
                                                                Stream.of(
                                                                        concept
                                                                );

                                                        Stream<Concept> atomicTerms =
                                                                Arrays.stream(
                                                                                voice.split(
                                                                                        "\\s*>\\s*"
                                                                                )
                                                                        )
                                                                        .map(String::trim)
                                                                        .filter(term ->
                                                                                !term.isBlank()
                                                                        )
                                                                        .filter(term ->
                                                                                !term.equals(
                                                                                        "DERECHO"
                                                                                )
                                                                        )
                                                                        .map(term ->
                                                                                new Concept(
                                                                                        term,
                                                                                        concept.score()
                                                                                )
                                                                        );

                                                        return Stream.concat(
                                                                fullVoice,
                                                                atomicTerms
                                                        );
                                                    })
                                                    /*
                                                     * distinct() no alcanza si Concept
                                                     * incluye score en equals().
                                                     *
                                                     * Queremos términos únicos.
                                                     */
                                                    .collect(
                                                            Collectors.toMap(
                                                                    Concept::term,
                                                                    Function.identity(),
                                                                    (first, second) ->
                                                                            first.score()
                                                                                    >= second.score()
                                                                                    ? first
                                                                                    : second,
                                                                    LinkedHashMap::new
                                                            )
                                                    )
                                                    .values()
                                                    .stream()
                                                    .toList();

                            return new BatchCandidateInput(
                                    input.id(),
                                    input.text(),
                                    llmCandidates
                            );
                        })
                        .toList();

        String data =
                buildBatchTextData(
                        llmInputs
                );

        String instructions =
                instructionProvider.get(
                        promptName
                );

        LlmRequest request =
                new LlmRequest(
                        instructions,
                        data,
                        buildBatchEnrichmentFormat(
                                llmInputs
                        )
                );

        return llmService
                .generate(
                        request
                )
                .map(response -> {

                    BatchEvaluation evaluation =
                            parseBatchEvaluation(
                                    response
                            );

                    Map<Integer, BatchSegmentEvaluation> evaluationsById =
                            new LinkedHashMap<>();

                    if (evaluation.segments() != null) {

                        for (BatchSegmentEvaluation segment :
                                evaluation.segments()) {

                            if (segment == null) {
                                continue;
                            }

                            evaluationsById.putIfAbsent(
                                    segment.id(),
                                    segment
                            );
                        }
                    }

                    return evaluationsById;
                });
    }

    /*
     * Devuelve solamente los segmentos con texto cuyo ID
     * no fue devuelto por el LLM.
     */
    private List<BatchCandidateInput> findMissingInputs(
            List<BatchCandidateInput> inputs,
            Map<Integer, BatchSegmentEvaluation> evaluationsById) {

        return inputs.stream()
                .filter(input ->
                        input.text() != null
                                && !input.text().isBlank()
                )
                .filter(input ->
                        !evaluationsById.containsKey(
                                input.id()
                        )
                )
                .toList();
    }

    /*
     * Reconstruye el resultado final respetando exactamente
     * el orden y la cantidad de los inputs originales.
     *
     * Si un segmento sigue faltando luego del retry, se usa
     * un enhancement vacío en lugar de abortar todo el fallo.
     */
    private List<LegalEnhancement> buildEnhancements(
            List<BatchCandidateInput> inputs,
            Map<Integer, BatchSegmentEvaluation> evaluationsById) {

        List<LegalEnhancement> result =
                new java.util.ArrayList<>(
                        inputs.size()
                );

        for (BatchCandidateInput input :
                inputs) {

            if (input.text() == null
                    || input.text().isBlank()) {

                result.add(
                        emptyEnhancement()
                );

                continue;
            }

            BatchSegmentEvaluation segmentEvaluation =
                    evaluationsById.get(
                            input.id()
                    );

            if (segmentEvaluation == null) {

                System.err.println(
                        "El LLM no devolvió el segmento "
                                + input.id()
                                + ". Se usa enhancement vacío."
                );

                result.add(
                        emptyEnhancement()
                );

                continue;
            }

//            List<String> selectedTerms =
//                    normalizeTerms(
//                            segmentEvaluation.terminos()
//                    );
//
//            List<Concept> voices =
//                    rebuildSupportedVoicesFromTerms(
//                            input.candidateVoices(),
//                            selectedTerms
//                    );
            
            List<String> selectedVoices =
                    normalizeTerms(
                            segmentEvaluation.terminos()
                    );

            Set<String> selectedVoiceSet =
                    Set.copyOf(
                            selectedVoices
                    );

            /*
             * Conjunto cerrado:
             * sólo aceptamos voces que estaban entre
             * los candidatos originales del segmento.
             */
            List<Concept> voices =
                    input.candidateVoices() == null
                            ? List.of()
                            : input.candidateVoices()
                                    .stream()
                                    .filter(
                                            Objects::nonNull
                                    )
                                    .filter(concept ->
                                            concept.term() != null
                                                    && selectedVoiceSet.contains(
                                                            concept.term().trim()
                                                    )
                                    )
                                    .toList();

            List<String> propositions =
                    normalizePropositions(
                            segmentEvaluation.propositions()
                    );

            String legalText =
                    formatLegalText(
                            voices,
                            propositions
                    );

            result.add(
                    new LegalEnhancement(
                            legalText,
                            voices,
                            propositions
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private List<String> normalizeTerms(
            List<String> terms) {

        if (terms == null
                || terms.isEmpty()) {

            return List.of();
        }

        return terms.stream()
                .filter(
                        Objects::nonNull
                )
                .map(
                        String::trim
                )
                .filter(term ->
                        !term.isBlank()
                )
                .distinct()
                .toList();
    }

    private List<String> normalizePropositions(
            List<String> propositions) {

        if (propositions == null
                || propositions.isEmpty()) {

            return List.of();
        }

        return propositions.stream()
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
    }

    private LegalEnhancement emptyEnhancement() {

        return new LegalEnhancement(
                "",
                List.of(),
                List.of()
        );
    }
    
    private String buildBatchTextData(
            List<BatchCandidateInput> inputs) {

        StringBuilder data =
                new StringBuilder();

        for (BatchCandidateInput input :
                inputs) {

            if (input.text() == null
                    || input.text().isBlank()) {

                continue;
            }

            List<String> voices =
                    input.candidateVoices() == null
                            ? List.of()
                            : input.candidateVoices()
                                    .stream()
                                    .filter(
                                            Objects::nonNull
                                    )
                                    .map(
                                            Concept::term
                                    )
                                    .filter(
                                            Objects::nonNull
                                    )
                                    .map(
                                            String::trim
                                    )
                                    .filter(voice ->
                                            !voice.isBlank()
                                    )
                                    .distinct()
                                    .toList();

            String voicesText =
                    voices.isEmpty()
                            ? "(sin voces)"
                            : String.join(
                                    "\n",
                                    voices
                            );

            data.append(
                    "=== SEGMENTO ===\n"
            );

            data.append(
                    "ID: "
            );

            data.append(
                    input.id()
            );

            data.append(
                    "\n\n"
            );

            data.append(
                    "=== TEXTO ===\n\n"
            );

            data.append(
                    input.text()
            );

            data.append(
                    "\n\n"
            );

            data.append(
                    "=== VOCES CANDIDATAS ===\n\n"
            );

            data.append(
                    voicesText
            );

            data.append(
                    "\n\n"
            );

            data.append(
                    "=== FIN SEGMENTO ===\n\n"
            );
        }

        return data
                .toString()
                .trim();
    }
    
    private String buildBatchTextData2(
            List<BatchCandidateInput> inputs) {

        StringBuilder data =
                new StringBuilder();

        for (BatchCandidateInput input :
                inputs) {

            if (input.text() == null
                    || input.text().isBlank()) {

                continue;
            }

            List<String> terms =
                    input.candidateVoices() == null
                            ? List.of()
                            : input.candidateVoices()
                                    .stream()
                                    .filter(
                                            Objects::nonNull
                                    )
                                    .map(
                                            Concept::term
                                    )
                                    .filter(
                                            Objects::nonNull
                                    )
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

            data.append(
                    "=== SEGMENTO ===\n"
            );

            data.append(
                    "ID: "
            );

            data.append(
                    input.id()
            );

            data.append(
                    "\n\n"
            );

            data.append(
                    "=== TEXTO ===\n\n"
            );

            data.append(
                    input.text()
            );

            data.append(
                    "\n\n"
            );

            data.append(
                    "=== TERMINOS CANDIDATOS ===\n\n"
            );

            data.append(
                    termsText
            );

            data.append(
                    "\n\n"
            );

            data.append(
                    "=== FIN SEGMENTO ===\n\n"
            );
        }

        return data
                .toString()
                .trim();
    }
    
    private List<Concept> rebuildSupportedVoicesFromTerms(
            List<Concept> candidateVoices,
            List<String> selectedTerms) {

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
                        : (int) candidateConcepts.stream()
                                .filter(Objects::nonNull)
                                .distinct()
                                .count();

        Map<String, Object> termsSchema =
                new LinkedHashMap<>();

        termsSchema.put(
                "type",
                "array"
        );

        termsSchema.put(
                "items",
                Map.of(
                        "type",
                        "string"
                )
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
    
    /*
     * =================================================
     * DATOS PARA EL PROMPT
     * =================================================
     */
    private String buildTextData(
            String text,
            List<String> candidateConcepts) {

        List<String> voices =
                candidateConcepts == null
                        ? List.of()
                        : candidateConcepts.stream()
                                .filter(
                                        Objects::nonNull
                                )
                                .map(
                                        String::trim
                                )
                                .filter(voice ->
                                        !voice.isBlank()
                                )
                                .distinct()
                                .toList();

        String voicesText =
                voices.isEmpty()
                        ? "(sin voces)"
                        : String.join(
                                "\n",
                                voices
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

    private String buildTextData2(
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
    
    private Map<String, Object> buildBatchEnrichmentFormat(
            List<BatchCandidateInput> inputs) {

        Map<String, Object> termsSchema =
                new LinkedHashMap<>();

        termsSchema.put(
                "type",
                "array"
        );

        termsSchema.put(
                "items",
                Map.of(
                        "type",
                        "string"
                )
        );

        termsSchema.put(
                "maxItems",
                60
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

        Map<String, Object> segmentProperties =
                new LinkedHashMap<>();

        segmentProperties.put(
                "id",
                Map.of(
                        "type",
                        "integer",
                        "minimum",
                        1,
                        "maximum",
                        inputs.size()
                )
        );

        segmentProperties.put(
                "terminos",
                termsSchema
        );

        segmentProperties.put(
                "propositions",
                propositionsSchema
        );

        Map<String, Object> segmentSchema =
                new LinkedHashMap<>();

        segmentSchema.put(
                "type",
                "object"
        );

        segmentSchema.put(
                "properties",
                segmentProperties
        );

        segmentSchema.put(
                "required",
                List.of(
                        "id",
                        "terminos",
                        "propositions"
                )
        );

        segmentSchema.put(
                "additionalProperties",
                false
        );

        Map<String, Object> segmentsSchema =
                new LinkedHashMap<>();

        segmentsSchema.put(
                "type",
                "array"
        );

        segmentsSchema.put(
                "items",
                segmentSchema
        );

        segmentsSchema.put(
                "maxItems",
                inputs.size()
        );

        Map<String, Object> properties =
                new LinkedHashMap<>();

        properties.put(
                "segments",
                segmentsSchema
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
                        "segments"
                )
        );

        schema.put(
                "additionalProperties",
                false
        );

        return schema;
    }
    
    private BatchEvaluation parseBatchEvaluation(
            String response) {

        if (response == null
                || response.isBlank()) {

            return new BatchEvaluation(
                    List.of()
            );
        }

        String json =
                response.trim();

        try {

            return objectMapper.readValue(
                    json,
                    BatchEvaluation.class
            );

        } catch (Exception firstException) {

            System.err.println(
                    "===== INVALID BATCH LLM JSON ====="
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
                        BatchEvaluation.class
                );

            } catch (Exception secondException) {

                throw new IllegalStateException(
                        "No se pudo interpretar "
                                + "la respuesta batch del LLM: "
                                + response,
                        secondException
                );
            }
        }
    }

    /*
     * =================================================
     * DTO INTERNOS
     * =================================================
     */
    
    private record BatchInput(
            int id,
            String text
    ) {
    }

    private record BatchCandidateInput(
            int id,
            String text,
            List<Concept> candidateVoices
    ) {
    }

    private record BatchSegmentEvaluation(
            int id,
            List<String> terminos,
            List<String> propositions
    ) {
    }

    private record BatchEvaluation(
            List<BatchSegmentEvaluation> segments
    ) {
    }


    private record TermEvaluation(
            List<String> terminos,
            List<String> propositions
    ) {
    }
}
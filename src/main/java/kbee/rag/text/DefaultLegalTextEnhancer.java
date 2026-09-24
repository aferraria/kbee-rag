package kbee.rag.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


import kbee.rag.llm.LlmBatchEnrichmentRequest;
import kbee.rag.llm.LlmEnrichmentRequest;
import kbee.rag.llm.LlmLawInterpretationRequest;
import kbee.rag.llm.LlmRequestFactory;
import kbee.rag.thesaurus.Concept;
import kbee.rag.thesaurus.ConceptList;
import kbee.rag.thesaurus.ThesaurusService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class DefaultLegalTextEnhancer
        implements LegalTextEnhancer {

    private static final Logger log =
            LoggerFactory.getLogger(LegalTextEnhancer.class);

    private final ThesaurusService thesaurusService;
    
    private final LlmRequestFactory llmRequestFactory;
    
    private static final String LAW_INTERPRETATION =
            "INTERPRETACION DE LA LEY";

    public DefaultLegalTextEnhancer(
            ThesaurusService conceptExtractorService,
            LlmRequestFactory llmRequestFactory) {

        this.thesaurusService =
                conceptExtractorService;

        this.llmRequestFactory = 
        		llmRequestFactory;
    }

    public Mono<TextEnhanced> enhance(
            String text) {

        if (text == null
                || text.isBlank()) {
            return Mono.just(
                    new TextEnhanced(
                            "",
                            
                            
                            
                            
                            
                            List.of(),
                            List.of()
                    )
            );
        }
        
        return thesaurusService
                .candidates(text)
                .defaultIfEmpty(
                		new ConceptList(List.of())
                )
                .flatMap(candidates ->
                        enhance(
                                text,
                                candidates.concepts()
                        )
                )
                .flatMap(enrichment ->
                        enhanceLawInterpretation(
                                enrichment
                        )
                )
                .map(enrichment -> {

                    String legalText =
                            formatLegalText(
                                    enrichment.voices(),
                                    enrichment.propositions()
                            );

                    return new TextEnhanced(
                            legalText,
                            enrichment.voices(),
                            enrichment.propositions()
                    );
                });
    }
    

    
    private Mono<TextEnhanced> enhanceLawInterpretation(
            TextEnhanced enhanced) {

        boolean alreadyPresent =
                enhanced.voices()
                        .stream()
                        .anyMatch(concept ->
                                LAW_INTERPRETATION.equalsIgnoreCase(
                                        concept.term()
                                )
                        );

        if (alreadyPresent
                || !containsLegalReference(
                        enhanced.text()
                )) {

            return Mono.just(
                    enhanced
            );
        }

        return lawInterpretation(
                enhanced.text()
        )
        .map(hasLawInterpretation -> {

            if (!hasLawInterpretation) {
                return enhanced;
            }

            List<Concept> voices =
                    new ArrayList<>(
                            enhanced.voices()
                    );

            voices.add(
                    new Concept(
                            LAW_INTERPRETATION,
                            1.0f
                    )
            );

            return new TextEnhanced(
                    enhanced.text(),
                    voices,
                    enhanced.propositions()
            );
        });
    }
    
    private Mono<TextEnhanced> enhance(
            String text,
            List<Concept> voices) {

        return llmRequestFactory.execute(
                LlmEnrichmentRequest.class,
                builder ->
                        builder
                                .input(text)
                                .input(voices)
        );
    }

    private Mono<Boolean> lawInterpretation(
            String text) {

        return llmRequestFactory.execute(
                LlmLawInterpretationRequest.class,
                builder ->
                        builder.input(text)
        );
    }
    
    
    
    public Mono<List<TextEnhanced>> enhance(
            List<String> texts) {

        long voicesStart =
                System.nanoTime();

        return getBatchVoices(texts)
                .collectList()
                .doOnNext(inputs -> {

                    long elapsedMs =
                            (System.nanoTime() - voicesStart)
                                    / 1_000_000;

                    log.info(
                            "VOICES PERF | texts={} | inputs={} | elapsedMs={}",
                            texts.size(),
                            inputs.size(),
                            elapsedMs
                    );
                })
                .flatMap(this::enhanceBatch)
                .flatMapMany(Flux::fromIterable)
                .flatMapSequential(
                        this::enhanceLawInterpretation,
                        2
                )
                .map(enrichment -> {

                    String legalText =
                            formatLegalText(
                                    enrichment.voices(),
                                    enrichment.propositions()
                            );

                    return new TextEnhanced(
                            legalText,
                            enrichment.voices(),
                            enrichment.propositions()
                    );
                })
                .collectList();
    }

    private Mono<List<TextEnhanced>> enhanceBatch(
            List<BatchCandidateInput> inputs) {

        return llmRequestFactory.execute(
                LlmBatchEnrichmentRequest.class,
                builder ->
                        builder.input(inputs)
        );
    }
    
    
    public Flux<BatchCandidateInput> getBatchVoices(
            List<String> texts) {

        List<BatchInput> inputs =
                IntStream
                        .range(0, texts.size())
                        .mapToObj(index ->
                                new BatchInput(
                                        index + 1,
                                        texts.get(index)
                                )
                        )
                        .toList();

        return Flux
                .fromIterable(inputs)
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

                    long start =
                            System.nanoTime();

                    return thesaurusService
                            .candidates(text)
                            .defaultIfEmpty(
                                    new ConceptList(
                                            List.of()
                                    )
                            )
                            .doOnNext(result -> {

                                long elapsedMs =
                                        (System.nanoTime() - start)
                                                / 1_000_000;

                                log.info(
                                        "CANDIDATES PERF | id={} | concepts={} | elapsedMs={}",
                                        input.id(),
                                        result.concepts().size(),
                                        elapsedMs
                                );
                            })
                            .map(extraction ->
                                    new BatchCandidateInput(
                                            input.id(),
                                            text,
                                            extraction.concepts()
                                    )
                            );
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
    	return false;
//        if (text == null || text.isBlank()) {
//            return false;
//        }
//
//        return LEGAL_REFERENCE_PATTERN
//                .matcher(text)
//                .find();
    }

    
    /*
     * =================================================
     * EXPANSION POR RELACIONES
     *  =================================================
     */
//    private List<Concept> expand(List<Concept> voices) {
//    	List<Concept> expanded = new ArrayList<>();
//    	for (Concept voice : voices) {
//    		if (voice.term().equals("PRUEBA > NEGLIGENCIA PROBATORIA")) {
//        		expanded.add(new Concept("PRUEBA > PRODUCCION > NEGLIGENCIA PROBATORIA", voice.score()));
//    		}
//    		expanded.add(voice);
//    	}
//    	return expanded;
//    }
    
  

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
     * DTO INTERNOS
     * =================================================
     */
    
    private record BatchInput(
            int id,
            String text
    ) {
    }

}
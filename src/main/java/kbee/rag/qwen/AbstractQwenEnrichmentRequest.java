package kbee.rag.qwen;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import kbee.rag.ollama.OllamaLlmRequest;
import kbee.rag.ollama.OllamaOptions;
import kbee.rag.thesaurus.Concept;


public abstract class AbstractQwenEnrichmentRequest<T>
        implements OllamaLlmRequest<T> {

    private static final int MAX_CANDIDATE_VOICES =
            40;
    
	public OllamaOptions options() {
		return new OllamaOptions(
		        0.0,
		        16384,
		        8192,
		        42
		);
	}

    protected List<String> buildLlmCandidates(
            List<Concept> candidates) {

        if (candidates == null
                || candidates.isEmpty()) {

            return List.of();
        }

        return candidates
                .stream()
                .filter(Objects::nonNull)
                .filter(concept ->
                        concept.term() != null
                                && !concept.term().isBlank()
                )
                .limit(
                        MAX_CANDIDATE_VOICES
                )
                .map(Concept::term)
                .map(String::trim)
                .flatMap(voice -> {
                	

//                    if ("PRUEBA PERICIAL MEDICA".equals(voice)) {
//                        voice =
//                                "PRUEBA PERICIAL MEDICA"
//                                + " [Definición: cuando se hace una pericia médico]";
//                    }

                    Stream<String> fullVoice =
                            Stream.of(
                                    voice
                            );
 
                    Stream<String> atomicTerms =
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
                                    !"DERECHO".equals(term)
                            );

                    return Stream.concat(
                            fullVoice,
                            atomicTerms
                    );
                })
                .distinct()
                .toList();
    }
    
    protected List<Concept> reconstructVoices(
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
    

    protected List<String> normalizePropositions(
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
    
  protected String repairJson(
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

}
package kbee.rag.thesaurus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class ThesaurusCandidateFilter {

    private static final Set<String> STOP_WORDS = Set.of(
            "DE",
            "DEL",
            "LA",
            "LAS",
            "EL",
            "LOS",
            "Y",
            "E"
    );

    /**
     * Elimina voces redundantes.
     */
    public List<ThesaurusCandidate> removeRedundantTerms(
            List<ThesaurusCandidate> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        /*
         * Limpia nulls y duplicados exactos.
         *
         * Si la misma voz aparece varias veces,
         * conserva la de mayor finalScore.
         */
        List<ThesaurusCandidate> normalized =
                candidates.stream()
                        .filter(candidate ->
                                candidate != null
                                && candidate.voice() != null
                                && !candidate.voice().isBlank()
                        )
                        .collect(
                                Collectors.toMap(
                                        candidate ->
                                                normalizeTerm(
                                                        candidate.voice()
                                                ),

                                        candidate ->
                                                new ThesaurusCandidate(
                                                        candidate.voice().trim(),
                                                        candidate.score(),
                                                        candidate.normalizedScore(),
                                                        candidate.finalScore()
                                                ),

                                        (a, b) ->
                                                a.finalScore()
                                                        >= b.finalScore()
                                                        ? a
                                                        : b,

                                        LinkedHashMap::new
                                )
                        )
                        .values()
                        .stream()
                        .toList();

        List<ThesaurusCandidate> result =
                new ArrayList<>();

        for (ThesaurusCandidate candidate :
                normalized) {

            boolean redundant = false;

            for (ThesaurusCandidate other :
                    normalized) {

                if (candidate == other) {
                    continue;
                }

                if (isMoreSpecific(
                        other.voice(),
                        candidate.voice())) {

                    redundant = true;
                    break;
                }
            }

            if (!redundant) {
                result.add(candidate);
            }
        }

        /*
         * Orden:
         *
         * 1. finalScore descendente
         * 2. voz ascendente
         */
        result.sort(
                Comparator
                        .comparingDouble(
                                ThesaurusCandidate::finalScore
                        )
                        .reversed()
                        .thenComparing(
                                candidate ->
                                        normalizeTerm(
                                                candidate.voice()
                                        )
                        )
        );

        return result;
    }

    private boolean isMoreSpecific(
            String specific,
            String generic) {

        if (specific == null
                || generic == null) {

            return false;
        }

        String normalizedSpecific =
                normalizeTerm(specific);

        String normalizedGeneric =
                normalizeTerm(generic);

        if (normalizedSpecific.equals(
                normalizedGeneric)) {

            return false;
        }

        Set<String> specificTokens =
                getSignificantTokens(
                        normalizedSpecific
                );

        Set<String> genericTokens =
                getSignificantTokens(
                        normalizedGeneric
                );

        if (specificTokens.isEmpty()
                || genericTokens.isEmpty()) {

            return false;
        }

        if (!specificTokens.containsAll(
                genericTokens)) {

            return false;
        }

        return specificTokens.size()
                > genericTokens.size();
    }

    private Set<String> getSignificantTokens(
            String voice) {

        if (voice == null || voice.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(
                        voice
                                .toUpperCase()
                                .replace('.', ' ')
                                .replace(',', ' ')
                                .replace(';', ' ')
                                .replace(':', ' ')
                                .split("[>\\s]+")
                )
                .map(String::trim)
                .filter(token ->
                        !token.isBlank()
                )
                .filter(token ->
                        !STOP_WORDS.contains(token)
                )
                .collect(
                        Collectors.toCollection(
                                LinkedHashSet::new
                        )
                );
    }

    private String normalizeTerm(
            String term) {

        if (term == null) {
            return "";
        }

        return term
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase();
    }

    /**
     * Construye el conceptual query después
     * de eliminar redundancias.
     *
     * primer concepto => ^10
     * resto           => ^1
     */
    public String buildConceptualQuery(
            List<ThesaurusCandidate> candidates) {

        List<ThesaurusCandidate> filtered =
                removeRedundantTerms(
                        candidates
                );

        if (filtered.isEmpty()) {
            return "";
        }

        List<String> clauses =
                new ArrayList<>();

        for (int i = 0;
             i < filtered.size();
             i++) {

            ThesaurusCandidate candidate =
                    filtered.get(i);

            String luceneTerms =
                    buildAndTerms(
                            candidate.voice()
                    );

            double boost =
                    i == 0
                            ? 10.0
                            : 1.0;

            clauses.add(
                    "(thesaurus_term:("
                    + luceneTerms
                    + "))^"
                    + boost
            );
        }

        return String.join(
                " OR ",
                clauses
        );
    }

    private String buildAndTerms(
            String voice) {

        return Arrays.stream(
                        voice
                                .replace(">", " ")
                                .trim()
                                .split("\\s+")
                )
                .map(String::trim)
                .filter(token ->
                        !token.isBlank()
                )
                .collect(
                        Collectors.joining(
                                " AND "
                        )
                );
    }
}
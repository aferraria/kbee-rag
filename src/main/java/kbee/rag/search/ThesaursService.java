package kbee.rag.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import kbee.rag.embedding.EmbeddingService;
import kbee.rag.llm.LlmRequest;
import kbee.rag.llm.LlmService;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class ThesaursService {

    /*
     * Cantidad máxima de voces que finalmente
     * utilizamos para expandir la consulta.
     */

    /*
     * Cantidad de voces que recuperamos inicialmente
     * por similitud vectorial.
     */

    private final EmbeddingService embeddingService;
    private final SolrClient solrClient;
    private final LlmService llmService;
    private final ThesaurusCandidateFilter thesaurusCandidateFilter;

    private final String targetCore;
    private final String embeddingField;

    public ThesaursService(
            ThesaurusCandidateFilter thesaurusCandidateFilter,
            EmbeddingService embeddingService,
            LlmService llmService,
            SolrClient solrClient,
            @Value("${solr.target.core}")
            String targetCore,
            @Value("${vector-search.embedding-field:embedding}")
            String embeddingField) {

        this.embeddingService = embeddingService;
        this.solrClient = solrClient;
        this.targetCore = targetCore;
        this.embeddingField = embeddingField;
        this.llmService = llmService;
        this.thesaurusCandidateFilter = thesaurusCandidateFilter;
     }

    public ConceptExtraction extract(
            String question) {

        if (question == null
                || question.isBlank()) {

            return new ConceptExtraction(
                    List.of()
            );
        }

        String thesaurusSearchText =
                buildThesaurusSearchText(
                        question
                );

        System.out.println(
                "===== THESAURUS SEARCH TEXT ====="
        );

        System.out.println(
                thesaurusSearchText
        );

        /*
         * 1. Recuperamos un conjunto amplio
         *    de voces por KNN.
         */
        List<ThesaurusCandidate> candidates =
                findVectorCandidates(
                        thesaurusSearchText,
                        2000
                );

        /*
         * 2. Expandimos la jerarquía.
         *
         *    Agregamos las voces candidatas
         *    y sus padres.
         */
        List<ThesaurusCandidate> expandedCandidates =
                expandHierarchy(
                        candidates
                );

        /*
         * 3. El LLM hace una selección amplia.
         *
         *    El LLM prioriza recall.
         *    Todavía puede devolver voces
         *    redundantes o relacionadas.
         */
        List<ThesaurusCandidate> llmSelected =
                selectWithLlm(
                        question,
                        expandedCandidates,
                        30
                );

        System.out.println(
                "===== THESAURUS LLM RAW SELECTION ====="
        );

        int i = 1;

        for (ThesaurusCandidate candidate : llmSelected) {

            System.out.println(
                    i++
                    + " | "
                    + candidate.voice()
                    + " | knnScore="
                    + candidate.score()
            );
        }

        /*
         * 4. Eliminamos redundancias
         *    de manera determinística.
         *
         * Ejemplos:
         *
         * PRUEBA > PRODUCCION > NEGLIGENCIA PROBATORIA
         * elimina
         * PRUEBA > NEGLIGENCIA PROBATORIA
         *
         * JUICIO > PARTES > CONDUCTA > NEGLIGENCIA PROCESAL
         * elimina
         * NEGLIGENCIA PROCESAL
         *
         * CORTE SUPREMA DE JUSTICIA PROVINCIAL > FUNCION REVISORA
         * elimina
         * CORTE SUPREMA DE JUSTICIA PROVINCIAL
         */
        List<ThesaurusCandidate> selected =
                thesaurusCandidateFilter.removeRedundantTerms(
                        llmSelected
                );

        System.out.println(
                "===== THESAURUS FINAL SELECTION ====="
        );

        i = 1;

        for (ThesaurusCandidate candidate : selected) {

            System.out.println(
                    i++
                    + " | "
                    + candidate.voice()
                    + " | knnScore="
                    + candidate.score()
            );
        }

        /*
         * 5. Convertimos las voces finales
         *    en conceptos.
         */
        List<Concept> concepts =
                selected.stream()
                        .map(candidate ->
                                new Concept(
                                        candidate.voice(),
                                        (float)candidate.finalScore()
                                )
                        )
                        .toList();

        return new ConceptExtraction(
                concepts
        );
    }
    
    public Mono<ConceptExtraction> candidates(
            String question) {

        if (question == null
                || question.isBlank()) {

            return Mono.just(
                    new ConceptExtraction(
                            List.of()
                    )
            );
        }

        return Mono.fromCallable(() -> {

            String thesaurusSearchText =
                    buildThesaurusSearchText(
                            question
                    );

            List<ThesaurusCandidate> candidates =
                    findCandidates(
                            thesaurusSearchText
                    );

            double bestScore =
                    candidates.isEmpty()
                            ? 0.0
                            : candidates.get(0).finalScore();

            double threshold =
                    bestScore * 0.95;

            List<Concept> concepts =
                    candidates.stream()
                            .filter(candidate ->
                                    candidate.finalScore()
                                            >= threshold
                            )
                            .limit(100)
                            .map(candidate ->
                                    new Concept(
                                            candidate.voice(),
                                            (float) candidate.finalScore()
                                    )
                            )
                            .toList();

            return new ConceptExtraction(
                    concepts
            );

        })
        .subscribeOn(
                Schedulers.boundedElastic()
        );
    }
    
    private List<ThesaurusCandidate> findCandidates(
            String segmentText) {

        Map<String, ThesaurusCandidate> merged =
                new LinkedHashMap<>();

        /*
         * Safety net global.
         */
        List<ThesaurusCandidate> globalCandidates =
                findVectorCandidates(
                        segmentText,
                        100
                );

        globalCandidates =
                expandHierarchy(
                        globalCandidates
                );

        globalCandidates =
                normalizeCandidates(
                        globalCandidates
                );

        mergeCandidates(
                merged,
                globalCandidates
        );

        /*
         * Búsquedas locales:
         * oración -> ventanas.
         */
        for (String sentence :
                splitSentences(segmentText)) {

            if (!isUsefulSentence(sentence)) {
                continue;
            }

            for (String window :
                    buildWindows(sentence)) {

//                System.out.println();
//                System.out.println(
//                        "===== THESAURUS WINDOW ====="
//              );
//
//                System.out.println(window);

                List<ThesaurusCandidate> candidates =
                        findVectorCandidates(
                                window,
                                20
                        );

                /*
                 * Primero expandimos la jerarquía
                 * dentro de esta búsqueda.
                 */
                candidates =
                        expandHierarchy(
                                candidates
                        );

                /*
                 * Después normalizamos el conjunto
                 * completo de esta window.
                 */
                candidates =
                        normalizeCandidates(
                                candidates
                        );

//                printCandidates(
//                        candidates
//                );

                mergeCandidates(
                        merged,
                        candidates
                );
            }
        }

        return merged.values()
                .stream()
                .sorted(
                        Comparator.comparingDouble(
                                ThesaurusCandidate::finalScore
                        ).reversed()
                )
                .toList();
    }
    

    
    private List<ThesaurusCandidate> normalizeCandidates(
            List<ThesaurusCandidate> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        double maxScore =
                candidates.stream()
                        .mapToDouble(
                                ThesaurusCandidate::score
                        )
                        .max()
                        .orElse(1.0);

        return candidates.stream()
                .map(candidate -> {

                    double normalizedScore =
                            candidate.score()
                            / maxScore;

                    double finalScore =
                            0.7 * normalizedScore
                            + 0.3 * candidate.score();

                    return new ThesaurusCandidate(
                            candidate.voice(),
                            candidate.score(),
                            normalizedScore,
                            finalScore
                    );
                })
                .toList();
    }
    
    private List<String> buildWindows(
            String sentence) {

        if (sentence == null
                || sentence.isBlank()) {

            return List.of();
        }

        String[] words =
                sentence.trim()
                        .split("\\s+");

        List<String> windows =
                new ArrayList<>();

        addWindows(
                windows,
                words,
                16,
                8,
                6
        );

        addWindows(
                windows,
                words,
                8,
                4,
                4
        );

        return windows.stream()
                .distinct()
                .toList();
    }

    private void addWindows(
            List<String> windows,
            String[] words,
            int windowSize,
            int step,
            int minLength) {

        if (words.length <= windowSize) {

            if (words.length >= minLength) {

                windows.add(
                        String.join(
                                " ",
                                words
                        )
                );
            }

            return;
        }

        for (int start = 0;
                start < words.length;
                start += step) {

            int end =
                    Math.min(
                            start + windowSize,
                            words.length
                    );

            int length =
                    end - start;

            if (length < minLength) {
                break;
            }

            windows.add(
                    String.join(
                            " ",
                            Arrays.copyOfRange(
                                    words,
                                    start,
                                    end
                            )
                    )
            );

            if (end == words.length) {
                break;
            }
        }
    }
    
    private void printCandidates(
            List<ThesaurusCandidate> candidates) {

        if (candidates == null
                || candidates.isEmpty()) {

            System.out.println(
                    "(sin candidatos)"
            );

            return;
        }

        int position = 1;

        for (ThesaurusCandidate candidate :
                candidates) {

            System.out.println(
                    position++
                            + " | "
                            + candidate.voice()
                            + " | knnScore="
                            + candidate.score()
            );
        }
    }

    
    private boolean isUsefulSentence(
            String sentence) {

        if (sentence == null) {
            return false;
        }

        String value =
                sentence.trim();

        return value.length() >= 40
                && value.length() <= 600;
    }
    
    private void mergeCandidates(
            Map<String, ThesaurusCandidate> merged,
            List<ThesaurusCandidate> candidates) {

        for (ThesaurusCandidate candidate :
                candidates) {

            merged.merge(
                    candidate.voice(),
                    candidate,
                    (current, incoming) ->
                            incoming.finalScore()
                                    > current.finalScore()
                                    ? incoming
                                    : current
            );
        }
    }
    
    private List<String> splitSentences(
            String text) {

        if (text == null
                || text.isBlank()) {

            return List.of();
        }

        return Arrays.stream(
                        text.split(
                                "(?<=[.!?])\\s+"
                        )
                )
                .map(String::trim)
                .filter(sentence ->
                        !sentence.isBlank()
                )
                .toList();
    }
    
    private String buildThesaurusSearchText(
            String question) {

        if (question == null || question.isBlank()) {
            return "";
        }

        String text = question.trim();

        /*
         * Frases de intención de búsqueda.
         * No describen el instituto jurídico buscado.
         */
        String[] removablePhrases = {
                "precedente sobre",
                "precedentes sobre",
                "jurisprudencia sobre",
                "fallo sobre",
                "fallos sobre",
                "requisitos que exige",
                "criterio de",
                "criterios de",

                /*
                 * Referencias institucionales.
                 */
                "corte suprema de justicia de santa fe",
                "corte suprema de santa fe",
                "corte suprema de justicia provincial",
                "corte suprema de justicia",
                "corte suprema"
        };

        String normalized =
                text.toLowerCase(Locale.ROOT);

        for (String phrase : removablePhrases) {

            normalized =
                    normalized.replace(
                            phrase,
                            " "
                    );
        }

        /*
         * Limpieza básica.
         */
        normalized =
                normalized
                        .replaceAll("[¿?¡!]", " ")
                        .replaceAll("\\s+", " ")
                        .trim();

        //return question;
        return normalized;
    }
    
    private List<ThesaurusCandidate> expandHierarchy(
            List<ThesaurusCandidate> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Map<String, Float> expanded =
                new LinkedHashMap<>();

        final float PARENT_FACTOR = 0.70f;

        for (ThesaurusCandidate candidate : candidates) {

            if (candidate == null
                    || candidate.voice() == null
                    || candidate.voice().isBlank()) {

                continue;
            }

            String term =
                    candidate.voice().trim();

            float currentScore =
                    (float) candidate.score();

            String current = term;

            while (current != null
                    && !current.isBlank()) {

                expanded.merge(
                        current,
                        currentScore,
                        Math::max
                );

                int separator =
                        current.lastIndexOf('>');

                if (separator < 0) {
                    break;
                }

                current =
                        current.substring(
                                0,
                                separator
                        ).trim();

                currentScore *= PARENT_FACTOR;
            }
        }

        return expanded.entrySet()
                .stream()
                .map(e -> new ThesaurusCandidate(
                        e.getKey(),
                        e.getValue()
                ))
                .sorted(
                        Comparator
                                .comparingDouble(
                                        ThesaurusCandidate::score
                                )
                                .reversed()
                                .thenComparing(
                                        ThesaurusCandidate::voice
                                )
                )
                .toList();
    }
    

    private List<ThesaurusCandidate> selectWithLlm(
            String question,
            List<ThesaurusCandidate> candidates,
            int topK) {

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        StringBuilder input = new StringBuilder();

        input.append("Pregunta:\n");
        input.append(question);
        input.append("\n\n");

        input.append("Voces candidatas del tesauro:\n");

        for (int i = 0; i < candidates.size(); i++) {

            input.append(i + 1)
                    .append(". ")
                    .append(candidates.get(i).voice())
                    .append("\n");
        }

        String instructions = """

        		```
        		    Sos un selector de conceptos jurídicos para recuperación
        		    de jurisprudencia.

        		    Recibís:

        		    - una pregunta jurídica;
        		    - una lista numerada de voces candidatas de un tesauro jurídico.

        		    Las voces pueden formar jerarquías padre-hijo.

        		    Tu tarea es seleccionar todas las voces candidatas que puedan
        		    representar razonablemente conceptos contenidos en la pregunta,
        		    hasta un máximo de %d voces.

        		    REGLAS OBLIGATORIAS:

        		    1. Seleccioná únicamente voces presentes en la lista suministrada.
        		       No inventes ni reformules voces.

        		    2. Priorizá los conceptos efectivamente expresados en la pregunta.

        		    3. No agregues conceptos solamente porque podrían estar jurídicamente
        		       relacionados con los hechos descriptos.

        		    4. No infieras responsabilidades, consecuencias, causas o valoraciones
        		       jurídicas que no tengan respaldo razonable en la pregunta.

        		    5. IMPORTANTE: priorizá el recall.
        		       Si una voz candidata puede representar razonablemente un concepto
        		       presente en la pregunta, incluila.

        		    6. Si existen varias voces relacionadas, similares o pertenecientes
   a una misma jerarquía, podés seleccionar varias cuando cada una
   represente razonablemente un concepto expresado en la pregunta.

   No selecciones una voz más específica únicamente porque sea hija
   o especialización de una voz general pertinente.

   Si la pregunta expresa solamente un concepto general y no aporta
   elementos que permitan identificar una de sus especies, no infieras
   ni enumeres esas especies específicas.

   La eliminación de redundancias entre voces efectivamente pertinentes
   se realizará posteriormente mediante reglas determinísticas.

        		    7. No descartes una voz únicamente porque exista otra más general,
        		       más específica o semánticamente similar.

        		    8. No confundas relación temática con identidad conceptual.

        		       Por ejemplo, que una pregunta mencione una pericia médica
        		       no implica responsabilidad médica, mala praxis ni culpa.

        		    9. Podés seleccionar voces correspondientes a distintos aspectos
        		       de la pregunta cuando cada una represente un concepto
        		       razonablemente relevante.

        		    10. Ante una duda razonable entre incluir o excluir una voz,
        		        incluila.

        		    FORMATO DE RESPUESTA:

        		    Respondé únicamente con los números de las voces seleccionadas,
        		    separados por comas.

        		    No agregues explicaciones, comentarios ni ningún otro texto.

        		    Ejemplo:

        		    3,12,31

        		    """.formatted(topK);

        String answer =
                llmService.generate(new LlmRequest(instructions, input.toString(), null)).block();

        return parseSelectedCandidates(
                answer,
                candidates,
                topK
        );
    }
    

    private List<ThesaurusCandidate> cutOnScoreDrop(
            List<ThesaurusCandidate> candidates,
            int minCandidates,
            float minAbsoluteDrop,
            float dropMultiplier) {

        if (candidates == null
                || candidates.isEmpty()) {

            return List.of();
        }

        if (candidates.size() <= minCandidates) {
            return candidates;
        }

        List<Float> previousDrops =
                new ArrayList<>();

        for (int i = 1;
                i < candidates.size();
                i++) {

            float previousScore =
                    (float)candidates.get(i - 1).finalScore();

            float currentScore =
            		(float)candidates.get(i).finalScore();

            float drop =
                    previousScore - currentScore;

            /*
             * No cortar demasiado pronto.
             */
            if (i >= minCandidates
                    && !previousDrops.isEmpty()) {

                float averageDrop =
                        (float) previousDrops.stream()
                                .mapToDouble(Float::doubleValue)
                                .average()
                                .orElse(0.0);

                boolean significantAbsoluteDrop =
                        drop >= minAbsoluteDrop;

                boolean abnormalRelativeDrop =
                        averageDrop > 0
                        && drop >= averageDrop
                                * dropMultiplier;

                if (significantAbsoluteDrop
                        && abnormalRelativeDrop) {

                    System.out.println(
                            "THESAURUS CUT"
                            + " position=" + (i + 1)
                            + " previousScore=" + previousScore
                            + " score=" + currentScore
                            + " drop=" + drop
                            + " avgDrop=" + averageDrop
                    );

                    return candidates.subList(
                            0,
                            i
                    );
                }
            }

            previousDrops.add(drop);
        }

        return candidates;
    }
    
    private List<ThesaurusCandidate> parseSelectedCandidates(
            String answer,
            List<ThesaurusCandidate> candidates,
            int topK) {

        if (answer == null || answer.isBlank()) {
            return List.of();
        }

        return java.util.Arrays.stream(
                        answer.split("[,\\s]+")
                )
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> {

                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(index ->
                        index >= 1
                        && index <= candidates.size()
                )
                .distinct()
                .limit(topK)
                .map(index ->
                        candidates.get(index - 1)
                )
                .toList();
    }
    
      
    
    public String expandForEmbedding(
            String question,
            ConceptExtraction extraction) {

        if (extraction == null
                || extraction.concepts() == null
                || extraction.concepts().isEmpty()) {

            return question;
        }

        String concepts =
                extraction.concepts()
                        .stream()
                        .map(Concept::term)
                        .collect(
                                Collectors.joining("\n")
                        );

        return question
                + "\n\n"
                + concepts;
    }
    
    public String buildLexicalQuery2(
            ConceptExtraction extraction) {

        if (extraction == null
                || extraction.concepts() == null
                || extraction.concepts().isEmpty()) {

            return "";
        }

        float bestScore =
                extraction.concepts()
                        .get(0)
                        .score();

        return extraction.concepts()
                .stream()
                .map((Concept concept) -> {

                    String term =
                            escapeSolrPhrase(
                                    concept.term()
                            );

                    double boost =
                            conceptualBoost(
                                    concept.score(),
                                    bestScore
                            );

                    return "thesaurus_term:\""
                            + term
                            + "\"^"
                            + String.format(
                                    Locale.US,
                                    "%.3f",
                                    boost
                            );
                })
                .collect(
                        Collectors.joining(" OR ")
                );
    }
    
    public String buildLexicalQuery(
            ConceptExtraction extraction) {

        if (extraction == null
                || extraction.concepts() == null
                || extraction.concepts().isEmpty()) {

            return "";
        }

        StringBuilder query =
                new StringBuilder();

        for (int i = 0;
             i < extraction.concepts().size();
             i++) {

            Concept concept =
                    extraction.concepts().get(i);

            if (concept == null
                    || concept.term() == null
                    || concept.term().isBlank()) {

                continue;
            }

            String conceptualTerms =
                    buildConceptTerms(
                            concept.term()
                    );

            if (conceptualTerms.isBlank()) {
                continue;
            }

            if (!query.isEmpty()) {
                query.append(" OR ");
            }

            query.append(
                    "thesaurus_term:("
            );

            query.append(
                    conceptualTerms
            );

            query.append(")");
        }

        return query.toString();
    }
    
    private String buildConceptTerms(
            String term) {

        if (term == null || term.isBlank()) {
            return "";
        }

        /*
         * Sacamos la sintaxis jerárquica del tesauro.
         *
         * PRUEBA > NEGLIGENCIA PROBATORIA
         *
         * pasa a:
         *
         * PRUEBA AND NEGLIGENCIA AND PROBATORIA
         */
        String normalized =
                term
                        .replace(">", " ")
                        .replace(".", " ")
                        .replaceAll("\\s+", " ")
                        .trim();

        return Arrays.stream(
                        normalized.split("\\s+")
                )
                .map(String::trim)
                .filter(value ->
                        !value.isBlank()
                )
                .map(this::escapeSolrTerm)
                .collect(
                        Collectors.joining(
                                " AND "
                        )
                );
    }
    
    private String escapeSolrTerm(
            String value) {

        return ClientUtils.escapeQueryChars(
                value
        );
    }

    private double conceptualBoost(
            float score,
            float bestScore) {

        if (bestScore <= 0f) {
            return 1.0;
        }

        double normalized =
                score / bestScore;

        return Math.pow(normalized, 8) * 10.0;
    }


    private String escapeSolrPhrase(String value) {

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private List<ThesaurusCandidate> findVectorCandidates(
            String question,
            int topK) {

        try {

            /*
             * Generamos el embedding de la pregunta.
             */
            List<List<Float>> embeddings =
                    embeddingService.embed(
                            List.of(question)
                    );

            if (embeddings == null
                    || embeddings.isEmpty()
                    || embeddings.get(0) == null
                    || embeddings.get(0).isEmpty()) {

                return List.of();
            }

            List<Float> vector =
                    embeddings.get(0);

            String vectorString =
                    toSolrVector(vector);

            /*
             * KNN únicamente contra documentos
             * que representan voces del tesauro.
             */
            ModifiableSolrParams params =
                    new ModifiableSolrParams();

            params.set(
                    "q",
                    "{!knn f="
                            + embeddingField
                            + " topK="
                            + topK
                            + "}"
                            + vectorString
            );

            params.set(
                    "fq",
                    "document_type:thesaurus"
            );

            params.set(
                    "fl",
                    "id,thesaurus_term,score"
            );

            params.set(
                    "rows",
                    topK
            );

            QueryRequest queryRequest =
                    new QueryRequest(
                            params,
                            SolrRequest.METHOD.POST
                    );

            QueryResponse response =
                    queryRequest.process(
                            solrClient,
                            targetCore
                    );

            return response
                    .getResults()
                    .stream()
                    .map(document -> {

                        Object termValue =
                                document.getFieldValue(
                                        "thesaurus_term"
                                );

                        Object scoreValue =
                                document.getFieldValue(
                                        "score"
                                );

                        String term =
                                extractThesaurusTerm(
                                        termValue
                                );

                        if (term == null
                                || term.isBlank()) {

                            return null;
                        }

                        float score = 0f;

                        if (scoreValue instanceof Number number) {

                            score =
                                    number.floatValue();
                        }

                        return new ThesaurusCandidate(
                                term,
                                score
                        );
                    })
                    .filter(Objects::nonNull)
                    .filter(candidate ->
                            !candidate.voice().isBlank()
                    )
                    .toList();

        } catch (Exception exception) {
        	exception.printStackTrace();
            throw new IllegalStateException(
                    "No fue posible realizar "
                            + "la búsqueda vectorial "
                            + "sobre el tesauro",
                    exception
            );
        }
    }
    
    private String extractThesaurusTerm(
            Object termValue) {

        if (termValue == null) {
            return null;
        }

        if (termValue instanceof java.util.Collection<?> collection) {

            return collection
                    .stream()
                    .findFirst()
                    .map(Object::toString)
                    .map(String::trim)
                    .orElse(null);
        }

        return termValue
                .toString()
                .trim();
    }

    private String toSolrVector(
            List<Float> vector) {

        return vector.stream()
                .map(String::valueOf)
                .collect(
                        Collectors.joining(
                                ",",
                                "[",
                                "]"
                        )
                );
    }
}
package kbee.rag.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
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
            kbee.rag.llm.LlmRegistry llmRegistry,
            SolrClient solrClient,
            @Value("${solr.target.core}")
            String targetCore,
            @Value("${vector-search.embedding-field:embedding}")
            String embeddingField) {

        this.embeddingService = embeddingService;
        this.solrClient = solrClient;
        this.targetCore = targetCore;
        this.embeddingField = embeddingField;
        this.llmService = llmRegistry.getDefault();
        this.thesaurusCandidateFilter = thesaurusCandidateFilter;
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
    
    public List<ThesaurusCandidate> findCandidates(
            String segmentText) {

        /*
         * =============================================
         * 1. Construimos todas las ventanas
         * =============================================
         */

        List<String> windows =
                new ArrayList<>();

        for (String sentence :
                splitSentences(segmentText)) {

            if (!isUsefulSentence(sentence)) {
                continue;
            }

            windows.addAll(
                    buildWindows(sentence)
            );
        }

        /*
         * =============================================
         * 2. Embedding batch
         * =============================================
         */

        List<String> textsToEmbed =
                new ArrayList<>(
                        windows.size() + 1
                );

        textsToEmbed.add(
                normalizeForEmbedding(
                        segmentText
                )
        );
        
        for (String window : windows) {

            textsToEmbed.add(
                    normalizeForEmbedding(
                            window
                    )
            );
        }
        
        List<List<Float>> embeddings =
                embeddingService.embed(
                        textsToEmbed
                );

        if (embeddings == null
                || embeddings.size()
                        != textsToEmbed.size()) {

            throw new IllegalStateException(
                    "Cantidad incorrecta de embeddings. "
                            + "Esperados="
                            + textsToEmbed.size()
                            + ", recibidos="
                            + (
                                embeddings == null
                                        ? 0
                                        : embeddings.size()
                            )
            );
        }

        /*
         * =============================================
         * 3. Candidatos vectoriales
         * =============================================
         */

        Map<String, ThesaurusCandidate> vectorMerged =
                new LinkedHashMap<>();

        /*
         * Safety net global.
         */
        List<ThesaurusCandidate> globalCandidates =
                findVectorCandidates(
                        embeddings.get(0),
                        100
                );

        globalCandidates =
                normalizeCandidates(
                        globalCandidates
                );

        mergeCandidates(
                vectorMerged,
                globalCandidates
        );

        /*
         * Búsquedas vectoriales por ventana.
         */
        for (int i = 0;
                i < windows.size();
                i++) {
        	
        	String window = windows.get(i);
//            System.out.println();
//            System.out.println(
//                    "WINDOW: [" + window + "]"
//            );

            List<ThesaurusCandidate> candidates =
                    findVectorCandidates(
                            embeddings.get(
                                    i + 1
                            ),
                            20
                    );
            candidates =
                    normalizeCandidates(
                            candidates
                    );
            
           // printCandidates(candidates);

            mergeCandidates(
                    vectorMerged,
                    candidates
            );
        }

        List<ThesaurusCandidate> vectorCandidates =
                vectorMerged.values()
                        .stream()
                        .sorted(
                                Comparator.comparingDouble(
                                        ThesaurusCandidate::finalScore
                                ).reversed()
                        )
                        .toList();

        /*
         * =============================================
         * 4. Candidatos léxicos por ventana
         *
         * No usamos un único ranking global.
         * Conservamos los resultados por ventana para
         * seleccionar luego de manera round-robin.
         * =============================================
         */

        List<List<ThesaurusCandidate>> lexicalByWindow =
                new ArrayList<>();

        for (String window : windows) {
        	
//            System.out.println();
//            System.out.println(
//                    "===== LEXICAL QUERY ====="
//            );
//            System.out.println(
//                    "WINDOW: [" + window + "]"
//            );

            List<ThesaurusCandidate> candidates =
                    findLexicalCandidates(
                            window,
                            10
                    );
            
  //          printCandidates(candidates);

            if (candidates != null
                    && !candidates.isEmpty()) {

                lexicalByWindow.add(
                        candidates
                );
            }
        }

        /*
         * =============================================
         * 5. Selección lexical diversificada
         *
         * Tomamos:
         *
         * ventana 1 -> candidato 1
         * ventana 2 -> candidato 1
         * ventana 3 -> candidato 1
         * ...
         *
         * luego:
         *
         * ventana 1 -> candidato 2
         * ventana 2 -> candidato 2
         * ...
         *
         * hasta completar 20 voces distintas.
         * =============================================
         */

        Map<String, ThesaurusCandidate> lexicalMerged =
                new LinkedHashMap<>();

        int lexicalRank = 0;

        while (lexicalMerged.size() < 20) {

            boolean foundCandidate = false;

            for (List<ThesaurusCandidate> candidates :
                    lexicalByWindow) {

                if (lexicalRank >= candidates.size()) {
                    continue;
                }

                foundCandidate = true;

                ThesaurusCandidate candidate =
                        candidates.get(
                                lexicalRank
                        );

                lexicalMerged.putIfAbsent(
                        candidate.voice(),
                        candidate
                );

                if (lexicalMerged.size() >= 20) {
                    break;
                }
            }

            /*
             * Ninguna ventana tiene más resultados
             * para este rank.
             */
            if (!foundCandidate) {
                break;
            }

            lexicalRank++;
        }

        List<ThesaurusCandidate> lexicalCandidates =
                new ArrayList<>(
                        lexicalMerged.values()
                );

        /*
         * =============================================
         * 6. Unión final
         *
         * 40 vectoriales
         * 20 lexicales
         * máximo 60 candidatos para el LLM
         * =============================================
         */

        Map<String, ThesaurusCandidate> finalCandidates =
                new LinkedHashMap<>();

        vectorCandidates.stream()
                .limit(40)
                .forEach(candidate ->
                        finalCandidates.putIfAbsent(
                                candidate.voice(),
                                candidate
                        )
                );

        lexicalCandidates.stream()
                .limit(20)
                .forEach(candidate ->
                        finalCandidates.putIfAbsent(
                                candidate.voice(),
                                candidate
                        )
                );

        /*
         * Si hubo coincidencias entre vectorial y lexical,
         * quedan lugares libres.
         *
         * Los completamos primero con vectoriales.
         */
        for (ThesaurusCandidate candidate :
                vectorCandidates) {

            if (finalCandidates.size() >= 60) {
                break;
            }

            finalCandidates.putIfAbsent(
                    candidate.voice(),
                    candidate
            );
        }

        /*
         * Y luego con lexicales si todavía queda lugar.
         */
        for (ThesaurusCandidate candidate :
                lexicalCandidates) {

            if (finalCandidates.size() >= 60) {
                break;
            }

            finalCandidates.putIfAbsent(
                    candidate.voice(),
                    candidate
            );
        }
        
//        System.out.println(
//                "===== FINAL ====="
//        );
//        printCandidates(
//                List.copyOf(finalCandidates.values())
//        );

        return new ArrayList<>(
                finalCandidates.values()
        );
    }
    
    public List<ThesaurusCandidate> findVectorCandidates(
            List<Float> vector,
            int topK) {

        try {

            if (vector == null
                    || vector.isEmpty()) {

                return List.of();
            }

            String vectorString =
                    toSolrVector(
                            vector
                    );

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

            long solrStart =
                    System.nanoTime();

            QueryResponse response =
                    queryRequest.process(
                            solrClient,
                            targetCore
                    );

            long solrElapsed =
                    System.nanoTime()
                            - solrStart;

//            System.out.printf(
//                    "SOLR KNN topK=%d time=%.3f s%n",
//                    topK,
//                    solrElapsed
//                            / 1_000_000_000.0
//            );

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

                        float score =
                                0f;

                        if (scoreValue
                                instanceof Number number) {

                            score =
                                    number.floatValue();
                        }

                        return new ThesaurusCandidate(
                                term,
                                score
                        );

                    })
                    .filter(
                            Objects::nonNull
                    )
                    .filter(candidate ->
                            !candidate.voice()
                                    .isBlank()
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
    
    public List<ThesaurusCandidate> findLexicalCandidates(
            String text,
            int topK) {

        try {

            if (text == null || text.isBlank()) {
                return List.of();
            }

            ModifiableSolrParams params =
                    new ModifiableSolrParams();

            String escapedWindow =
                    ClientUtils.escapeQueryChars(text);

            params.set(
                    "q",
                    escapedWindow
            );

            params.set(
                    "df",
                    "thesaurus_es_term"
            );

            params.set(
                    "q.op",
                    "OR"
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

                        if (scoreValue
                                instanceof Number number) {
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
                            + "la búsqueda léxica "
                            + "sobre el tesauro",
                    exception
            );
        }
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
        
//        addWindows(
//                windows,
//                words,
//                7,
//                3,
//                4
//        );
        
        addWindows(windows, words, 10, 5, 3);

        /*
         * Ventanas algo más amplias:
         * conservan contexto cuando hace falta.
         */
//        addWindows(
//                windows,
//                words,
//                10,
//                5,
//                5
//        );


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

        return value.length() >= 40;
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

    public List<ThesaurusCandidate> findVectorCandidates(
            String question,
            int topK) {

        try {

            /*
             * =============================================
             * EMBEDDING
             * =============================================
             */

            long embeddingStart =
                    System.nanoTime();

            String normalizedQuestion =
                    normalizeForEmbedding(
                            question
                    );

            List<List<Float>> embeddings =
                    embeddingService.embed(
                            List.of(normalizedQuestion)
                    );
            
            long embeddingElapsed =
                    System.nanoTime()
                            - embeddingStart;

            System.out.printf(
                    "EMBEDDING chars=%d time=%.3f s%n",
                    question == null
                            ? 0
                            : question.length(),
                    embeddingElapsed
                            / 1_000_000_000.0
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
             * =============================================
             * SOLR KNN
             * =============================================
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

            long solrStart =
                    System.nanoTime();

            QueryResponse response =
                    queryRequest.process(
                            solrClient,
                            targetCore
                    );

            long solrElapsed =
                    System.nanoTime()
                            - solrStart;

            System.out.printf(
                    "SOLR KNN topK=%d time=%.3f s%n",
                    topK,
                    solrElapsed
                            / 1_000_000_000.0
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
    
    private String normalizeForEmbedding(
            String text) {

        try (Analyzer analyzer =
                new SpanishAnalyzer()) {

            try (TokenStream tokenStream =
                    analyzer.tokenStream(
                            "text",
                            text
                    )) {

                CharTermAttribute term =
                        tokenStream.addAttribute(
                                CharTermAttribute.class
                        );

                StringBuilder result =
                        new StringBuilder();

                tokenStream.reset();

                while (tokenStream.incrementToken()) {

                    if (!result.isEmpty()) {
                        result.append(' ');
                    }

                    result.append(
                            term.toString()
                    );
                }

                tokenStream.end();

                return result.toString();
            }

        } catch (IOException e) {

            throw new IllegalStateException(
                    "No fue posible normalizar texto "
                            + "para embedding",
                    e
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

        StringBuilder sb =
                new StringBuilder(
                        vector.size() * 12
                );

        sb.append('[');

        for (int i = 0;
                i < vector.size();
                i++) {

            if (i > 0) {
                sb.append(',');
            }

            sb.append(
                    vector.get(i)
            );
        }

        sb.append(']');

        return sb.toString();
    }
}
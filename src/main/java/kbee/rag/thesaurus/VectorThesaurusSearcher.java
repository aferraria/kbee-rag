package kbee.rag.thesaurus;

import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import kbee.rag.embedding.EmbeddingService;
import reactor.core.publisher.Mono;

@Service
public class VectorThesaurusSearcher
        implements ThesaurusSearcher {

    private final EmbeddingService embeddingService;

    private final List<ConceptRecord> concepts;

    private final int topK;

    public VectorThesaurusSearcher(
            ThesaurusDao thesaurusDao,
            EmbeddingService embeddingService,
            @Value("${thesaurus.vector.top-k:60}")
            int topK) {

        this.embeddingService =
                embeddingService;

        this.topK =
                topK;

        /*
         * El tesauro se carga una sola vez
         * desde Solr y queda en memoria.
         */
        this.concepts =
                thesaurusDao.getConcepts();
    }

    @Override
    public Mono<List<Concept>> findCandidates(
            String text) {

        if (text == null
                || text.isBlank()) {

            return Mono.just(
                    List.of()
            );
        }

        return embeddingService
                .embedReactive(text)
                .map(this::findNearest);
    }

    private List<Concept> findNearest(
            List<Float> queryEmbedding) {

        List<Concept> candidates =
                concepts.stream()
                        .filter(concept ->
                                concept.embedding() != null
                                        && !concept.embedding().isEmpty()
                        )
                        .map(concept ->
                                new ScoredConcept(
                                        concept,
                                        cosineSimilarity(
                                                queryEmbedding,
                                                concept.embedding()
                                        )
                                )
                        )
                        .sorted(
                                Comparator.comparingDouble(
                                        ScoredConcept::score
                                ).reversed()
                        )
                        .limit(topK)
                        .map(scored ->
                                new Concept(
                                        scored.concept().term(),
                                        scored.score()
                                )
                        )
                        .toList();

        return normalize(candidates);
    }

    private List<Concept> normalize(
            List<Concept> candidates) {

        if (candidates.isEmpty()) {
            return candidates;
        }

        double maxScore =
                candidates.stream()
                        .mapToDouble(Concept::score)
                        .max()
                        .orElse(1.0);

        if (maxScore <= 0.0) {
            return candidates;
        }

        return candidates.stream()
                .map(candidate ->
                        new Concept(
                                candidate.term(),
                                (float) (
                                        candidate.score()
                                                / maxScore
                                )
                        )
                )
                .toList();
    }

    private float cosineSimilarity(
            List<Float> a,
            List<Float> b) {

        if (a.size() != b.size()) {

            throw new IllegalArgumentException(
                    "Dimensiones incompatibles: "
                            + a.size()
                            + " != "
                            + b.size()
            );
        }

        double dot =
                0.0;

        double normA =
                0.0;

        double normB =
                0.0;

        for (int i = 0;
                i < a.size();
                i++) {

            float av =
                    a.get(i);

            float bv =
                    b.get(i);

            dot +=
                    av * bv;

            normA +=
                    av * av;

            normB +=
                    bv * bv;
        }

        if (normA == 0.0
                || normB == 0.0) {

            return 0.0f;
        }

        return (float) (
                dot
                        / (
                        Math.sqrt(normA)
                                * Math.sqrt(normB)
                )
        );
    }

    private record ScoredConcept(
            ConceptRecord concept,
            float score
    ) {
    }
}
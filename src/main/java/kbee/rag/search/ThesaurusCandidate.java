package kbee.rag.search;

public record ThesaurusCandidate(
        String voice,
        double score,
        double normalizedScore,
        double finalScore
) {

    public ThesaurusCandidate(
            String voice,
            double score) {

        this(
                voice,
                score,
                score,
                score
        );
    }
}
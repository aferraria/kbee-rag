package kbee.rag.thesaurus;

import java.util.List;

public record ConceptRecord(
        String id,
        String term,
        List<Float> embedding
) {
}
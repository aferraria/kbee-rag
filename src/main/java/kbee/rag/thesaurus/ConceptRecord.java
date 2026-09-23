package kbee.rag.thesaurus;
public record ConceptRecord(
        String id,
        String term,
        Float[] vector
) {
}
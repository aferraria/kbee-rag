package kbee.rag.thesaurus;

import java.util.List;

public record ConceptExpansion(
        List<String> originalConcepts,
        List<String> searchExpansions) {
}
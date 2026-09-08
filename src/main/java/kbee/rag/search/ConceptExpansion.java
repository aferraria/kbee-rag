package kbee.rag.search;

import java.util.List;

public record ConceptExpansion(
        List<String> originalConcepts,
        List<String> searchExpansions) {
}
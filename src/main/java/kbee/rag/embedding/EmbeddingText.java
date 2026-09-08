package kbee.rag.embedding;

import java.util.List;

import kbee.rag.search.Concept;

// Antes de llamar al modelo de embeddings
public record EmbeddingText(
        EmbeddingTextType type,
        String text,
        List<Concept> thesaurusTerms,
        List<String> propositions
) {
}
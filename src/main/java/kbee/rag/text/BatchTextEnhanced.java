package kbee.rag.text;

import java.util.List;

import kbee.rag.thesaurus.Concept;

public record BatchTextEnhanced(
        int id,
        String text,
        List<Concept> voices,
        List<String> propositions
) {
}
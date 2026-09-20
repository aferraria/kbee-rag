package kbee.rag.text;

import java.util.List;

import kbee.rag.thesaurus.Concept;

public record TextEnhanced(
		String text,
        List<Concept> voices,
        List<String> propositions
) {
}
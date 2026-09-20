package kbee.rag.embedding;

import java.util.List;

import kbee.rag.thesaurus.Concept;

public record SegmentEnrichment(
        List<Concept> voices,
        List<String> propositions
) {
}
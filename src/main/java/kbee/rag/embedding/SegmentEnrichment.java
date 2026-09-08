package kbee.rag.embedding;

import java.util.List;

import kbee.rag.search.Concept;

public record SegmentEnrichment(
        List<Concept> voices,
        List<String> propositions
) {
}
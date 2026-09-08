package kbee.rag.segment;

import java.time.OffsetDateTime;
import java.util.List;

import kbee.rag.search.Concept;

public record TextSegment(
        String documentId,
        String documentTitle,
        OffsetDateTime documentDate,
        String sectionId,
        String sectionTitle,
        String sectionPath,
        int segmentNumber,
        int sectionSegmentNumber,
        String text,
        String embeddingText,
        String documentType,
        List<Concept> concepts,
        List<String> propositions
) {

    public TextSegment {

        concepts =
                concepts == null
                        ? List.of()
                        : List.copyOf(concepts);

        propositions =
                propositions == null
                        ? List.of()
                        : List.copyOf(propositions);
    }
}
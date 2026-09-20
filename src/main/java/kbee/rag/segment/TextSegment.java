package kbee.rag.segment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import kbee.rag.thesaurus.Concept;

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
        List<String> propositions,
        Map<String, Object> metainfo
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
        
        metainfo =
                metainfo == null
                        ? Map.of()
                        : Map.copyOf(metainfo);
    }
}
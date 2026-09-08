package kbee.rag.search;

import java.time.OffsetDateTime;

public record SegmentSearchResult(
        String id,
        String documentId,
        String documentTitle,
        OffsetDateTime documentDate,
        String sectionId,
        String sectionTitle,
        String sectionPath,
        Integer segmentNumber,
        Integer sectionSegmentNumber,
        String text,
        Float score) {
}
package kbee.rag.search;

import java.time.OffsetDateTime;

public record Source(
        String documentId,
        String documentTitle,
        OffsetDateTime documentDate,
        float score
) {
}
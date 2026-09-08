package kbee.rag.embedding;

import java.util.List;

import kbee.rag.segment.TextSegment;

public record SegmentEmbeddingTexts(
        TextSegment segment,
        List<EmbeddedText> texts
) {
}
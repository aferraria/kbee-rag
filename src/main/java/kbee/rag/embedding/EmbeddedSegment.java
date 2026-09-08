package kbee.rag.embedding;

import java.util.List;

import kbee.rag.segment.TextSegment;

public record EmbeddedSegment(
        TextSegment segment,
        List<EmbeddedText> embeddings
) {
}
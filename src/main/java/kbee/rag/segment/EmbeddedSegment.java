package kbee.rag.segment;

import java.util.List;

public record EmbeddedSegment(
        TextSegment segment,
        List<Float> embedding,
        List<Float> legalEmbedding
) {
}
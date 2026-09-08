package kbee.rag.embedding;

import java.util.List;

public record EmbeddedText(
        EmbeddingTextType type,
        List<Float> embedding
) {
}
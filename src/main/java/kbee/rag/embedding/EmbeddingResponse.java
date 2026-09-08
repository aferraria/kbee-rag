package kbee.rag.embedding;

import java.util.List;

public record EmbeddingResponse(
        List<List<Float>> embeddings) {
}
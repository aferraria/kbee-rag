package kbee.rag.embedding;

import java.util.List;

public record EmbeddingRequest(
        List<String> texts) {
}
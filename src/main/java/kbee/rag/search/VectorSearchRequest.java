package kbee.rag.search;

public record VectorSearchRequest(
        String text,
        Integer topK) {
}
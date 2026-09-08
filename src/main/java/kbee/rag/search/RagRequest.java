package kbee.rag.search;

import java.util.Map;

public record RagRequest(
        String question,
        Map<String, String> parameters,
        Integer topK
) {
}
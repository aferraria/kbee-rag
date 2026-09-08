package kbee.rag.llm;

import java.util.Map;

public record LlmRequest(
        String instructions,
        String input,
        Map<String, Object> format
) {
}
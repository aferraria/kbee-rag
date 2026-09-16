package kbee.rag.llm;

import java.util.Map;

public record LlmRequest(
        String instructions,
        String input,
        Map<String, Object> format,
        String reasoningEffort
) {

    /** Backwards-compatible constructor: defaults reasoningEffort to null (provider uses "low"). */
    public LlmRequest(
            String instructions,
            String input,
            Map<String, Object> format) {
        this(instructions, input, format, null);
    }
}
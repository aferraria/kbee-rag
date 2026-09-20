package kbee.rag.llm;

import java.util.Map;

public record LlmRequestOld(
        String instructions,
        String input,
        Map<String, Object> format,
        String reasoningEffort
) {

    /** Backwards-compatible constructor: defaults reasoningEffort to null (provider uses "low"). */
    public LlmRequestOld(
            String instructions,
            String input,
            Map<String, Object> format) {
        this(instructions, input, format, null);
    }
}
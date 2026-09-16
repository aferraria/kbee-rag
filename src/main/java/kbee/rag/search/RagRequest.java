package kbee.rag.search;

import java.util.Map;

public record RagRequest(
        String question,
        Map<String, String> parameters,
        Integer topK,
        String llm,
        String reasoningEffort
) {

    /** Default reasoning effort used when the request does not specify one. */
    public static final String DEFAULT_REASONING_EFFORT = "low";

    /** Returns the requested reasoning effort, or "low" if not specified. */
    public String reasoningEffortOrDefault() {
        return (reasoningEffort == null || reasoningEffort.isBlank())
                ? DEFAULT_REASONING_EFFORT
                : reasoningEffort;
    }
}
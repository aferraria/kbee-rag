package kbee.rag.llm;
public record LlmOptions(
        double temperature,
        int contextSize,
        int maxOutputTokens,
        int seed
) {
}
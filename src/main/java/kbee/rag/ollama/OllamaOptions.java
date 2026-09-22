package kbee.rag.ollama;
public record OllamaOptions(
        double temperature,
        int contextSize,
        int maxOutputTokens,
        int seed
) {

    public static OllamaOptions defaults() {
        return new OllamaOptions(
                0.0,
                48000,
                8192,
                42
        );
    }
}
package kbee.rag.config;
public interface InstructionProvider {

    String get(String name);

    /**
     * Provider-specific prompt: looks up
     * prompts/{provider}/{name}.txt and falls back
     * to the shared prompts/{name}.txt.
     */
    default String get(String provider, String name) {
        return get(name);
    }
}
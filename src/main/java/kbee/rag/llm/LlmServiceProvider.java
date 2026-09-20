package kbee.rag.llm;
public interface LlmServiceProvider {

    LlmService get(
            String provider
    );
}
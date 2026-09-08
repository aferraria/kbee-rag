package kbee.rag.llm;

public class LlmException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LlmException(String message) {
        super(message);
    }

    public LlmException(
            String message,
            Throwable cause) {

        super(message, cause);
    }
}
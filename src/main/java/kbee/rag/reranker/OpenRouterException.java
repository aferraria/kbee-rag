package kbee.rag.reranker;

@SuppressWarnings("serial")
public class OpenRouterException
        extends RuntimeException {

    private final int statusCode;

    public OpenRouterException(
            int statusCode,
            String responseBody) {

        super(
                "OpenRouter HTTP "
                        + statusCode
                        + ": "
                        + responseBody
        );

        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
package kbee.rag.search;

import java.util.List;

public record RagResponse(
        String queryId,
        String question,
        String answer,
        List<Source> sources
) {
    /** convenience constructor used before the server assigns the query id */
    public RagResponse(String question, String answer, List<Source> sources) {
        this(null, question, answer, sources);
    }

    /** copy of this response with the given query id */
    public RagResponse withQueryId(String queryId) {
        return new RagResponse(queryId, question, answer, sources);
    }
}
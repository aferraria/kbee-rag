package kbee.rag.search;

import java.util.List;

public record RagResponse(
        String question,
        String answer,
        List<Source> sources
) {
}
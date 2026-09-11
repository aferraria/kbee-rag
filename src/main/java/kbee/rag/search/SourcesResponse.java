package kbee.rag.search;

import java.util.List;

public record SourcesResponse(
        String question,
        List<Source> sources
) {
}
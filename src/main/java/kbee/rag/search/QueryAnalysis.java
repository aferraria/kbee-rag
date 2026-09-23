package kbee.rag.search;

import java.time.OffsetDateTime;

public record QueryAnalysis(
		String queryId,
		String llm,
		OffsetDateTime dateCreated,
		long totalTime,
		String analysis) {
}

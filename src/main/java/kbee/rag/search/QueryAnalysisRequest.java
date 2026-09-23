package kbee.rag.search;
public record QueryAnalysisRequest(
		String queryId,
		String llm) {
}

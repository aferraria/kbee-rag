package kbee.rag.search;
public record DocumentAnalysisRequest(
		String documentId,
		String question,
		String llm) {
}
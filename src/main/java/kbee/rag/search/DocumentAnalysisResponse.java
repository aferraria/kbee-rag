package kbee.rag.search;
public record DocumentAnalysisResponse(
        String documentId,
        String documentTitle,
        String answer) {
}
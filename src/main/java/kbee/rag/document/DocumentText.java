package kbee.rag.document;

public record DocumentText(
        String documentId,
        String documentTitle,
        String text
) {
}
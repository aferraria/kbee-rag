package kbee.rag.search;

public class RagTimings {

    private long queryExpansionMs;

    private long searchMs;

    private long documentExpansionMs;

    private long rerankerMs;

    private long totalMs;

    public long queryExpansionMs() {
        return queryExpansionMs;
    }

    public void queryExpansionMs(
            long queryExpansionMs) {

        this.queryExpansionMs =
                queryExpansionMs;
    }

    public long searchMs() {
        return searchMs;
    }

    public void searchMs(
            long searchMs) {

        this.searchMs =
                searchMs;
    }

    public long documentExpansionMs() {
        return documentExpansionMs;
    }

    public void documentExpansionMs(
            long documentExpansionMs) {

        this.documentExpansionMs =
                documentExpansionMs;
    }

    public long rerankerMs() {
        return rerankerMs;
    }

    public void rerankerMs(
            long rerankerMs) {

        this.rerankerMs =
                rerankerMs;
    }

    public long totalMs() {
        return totalMs;
    }

    public void totalMs(
            long totalMs) {

        this.totalMs =
                totalMs;
    }
}
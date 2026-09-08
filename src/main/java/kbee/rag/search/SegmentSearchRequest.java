package kbee.rag.search;

import java.util.List;

public class SegmentSearchRequest {

    private final String query;
    private final List<String> filters;
    private final int topK;

    public SegmentSearchRequest(
            String query,
            List<String> filters,
            int topK) {

        this.query = query;
        this.filters = filters;
        this.topK = topK;
    }

    public String query() {
        return query;
    }
    
    public List<String> filters() {
        return filters;
    }

    public int topK() {
        return topK;
    }
}
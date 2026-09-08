package kbee.rag.model;
import java.util.List;
import java.util.Map;
public record SolrQueryResponse(long numFound, long start, long elapsedTimeMs, List<Map<String,Object>> documents) {}

package kbee.rag.search;

import org.apache.solr.common.params.SolrParams;

import kbee.rag.segment.EmbeddedSegment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SegmentDao {

    public Flux<SegmentSearchResult> findSegments(
            String documentId,
            int from,
            int to); 
    
    Mono<Void> add(
            EmbeddedSegment segment
    );

    Mono<Void> deleteByDocumentId(
            String documentId
    );

    Mono<Void> commit();
    
    Flux<SegmentSearchResult> search(
            SolrParams params
    );
    
    Flux<SegmentSearchResult> findDocumentSegments(
            String documentId);
}
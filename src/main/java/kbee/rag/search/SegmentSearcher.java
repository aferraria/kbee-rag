package kbee.rag.search;

import reactor.core.publisher.Flux;

public interface SegmentSearcher {

    Flux<SegmentSearchResult> search(
            SegmentSearchRequest request
    );
}
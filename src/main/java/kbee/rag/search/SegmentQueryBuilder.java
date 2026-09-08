package kbee.rag.search;

import reactor.core.publisher.Mono;

public interface SegmentQueryBuilder {

    Mono<ExtendedSegmentSearchRequest> build(
            SegmentSearchRequest request
    );
}
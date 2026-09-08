package kbee.rag.segment;

import reactor.core.publisher.Mono;

public interface SegmentEnhancer {

    Mono<TextSegment> enhance(
            TextSegment segment
    );
}
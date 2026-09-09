package kbee.rag.segment;

import java.util.List;

import reactor.core.publisher.Mono;

public interface SegmentEnhancer {

    Mono<TextSegment> enhance(
            TextSegment segment
    );
    
    Mono<List<TextSegment>> enhance(
            List<TextSegment> segments
    );
}
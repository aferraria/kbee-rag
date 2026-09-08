package kbee.rag.segment;


import kbee.rag.io.TextFile;
import reactor.core.publisher.Flux;


public interface Segmenter {

    Flux<TextSegment> split(
            TextFile file,
            int segmentSize,
            int overlap
    );
}

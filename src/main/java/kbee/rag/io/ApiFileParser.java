package kbee.rag.io;

import reactor.core.publisher.Flux;

public interface ApiFileParser {

    Flux<TextFile> parse(
            ApiFile file
    );
}
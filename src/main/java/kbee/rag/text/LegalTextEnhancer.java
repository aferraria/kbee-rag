package kbee.rag.text;

import java.util.List;

import reactor.core.publisher.Mono;

public interface LegalTextEnhancer {

    Mono<TextEnhanced> enhance(
            String text
    );

    Mono<List<TextEnhanced>> enhance(
            List<String> texts
    );
}
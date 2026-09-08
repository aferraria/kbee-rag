package kbee.rag.text;

import reactor.core.publisher.Mono;

public interface LegalTextEnhancer {

    Mono<LegalEnhancement> enhance(
            String text,
            String promptName
    );
}
package kbee.rag.text;

import java.util.List;

import reactor.core.publisher.Mono;

public interface LegalTextEnhancer {

    Mono<LegalEnhancement> enhance(
            String text,
            String promptName
    );

    Mono<List<LegalEnhancement>> enhance(
            List<String> texts,
            String promptName
    );
}
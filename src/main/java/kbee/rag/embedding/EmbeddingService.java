package kbee.rag.embedding;

import java.util.List;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public interface EmbeddingService {

    List<List<Float>> embed(
            List<String> texts
    );

    default Mono<List<List<Float>>> embedReactive(
            List<String> texts) {

        return Mono.fromCallable(
                () -> embed(texts)
        ).subscribeOn(
                Schedulers.boundedElastic()
        );
    }

    default Mono<List<Float>> embedReactive(
            String text) {

        return embedReactive(
                List.of(text)
        )
        .map(embeddings -> {

            if (embeddings.isEmpty()) {
                throw new IllegalStateException(
                        "EmbeddingService no devolvió embedding"
                );
            }

            return embeddings.get(0);
        });
    }
}
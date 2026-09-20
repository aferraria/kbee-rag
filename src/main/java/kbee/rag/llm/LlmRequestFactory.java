package kbee.rag.llm;

import java.util.function.Function;

import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

public interface LlmRequestFactory {

    <T> LlmRequestBuilder<T> builder(
            String provider,
            Class<? extends LlmRequest<T>> requestType
    );

    String defaultProvider();

    default String resolveProvider(
            ContextView context) {

        return context.getOrDefault(
                "llm",
                defaultProvider()
        );
    }

    default <T> Mono<LlmRequestBuilder<T>> builder(
            Class<? extends LlmRequest<T>> requestType) {

        return Mono.deferContextual(context -> {

            String provider =
                    resolveProvider(context);

            return Mono.just(
                    builder(
                            provider,
                            requestType
                    )
            );
        });
    }

    default <T> Mono<T> execute(
            Class<? extends LlmRequest<T>> requestType,
            Function<
                    LlmRequestBuilder<T>,
                    LlmRequestBuilder<T>
            > configurer) {

        return Mono.deferContextual(context -> {

            String provider =
                    resolveProvider(context);

            LlmRequestBuilder<T> builder =
                    builder(
                            provider,
                            requestType
                    );

            return configurer
                    .apply(builder)
                    .build()
                    .execute();
        });
    }
}

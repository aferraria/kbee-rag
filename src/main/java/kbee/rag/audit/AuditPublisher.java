package kbee.rag.audit;

import reactor.core.publisher.Mono;

public interface AuditPublisher {

    Mono<Void> publish(
            AuditEvent event
    );
}
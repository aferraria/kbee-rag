package kbee.rag.audit;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class ReactorAuditPublisher
        implements AuditPublisher {

    public static final String AUDIT_CONTEXT_KEY =
            "rag.audit.context";

    private final AuditCollector collector;

    public ReactorAuditPublisher(
            AuditCollector collector) {

        this.collector = collector;
    }

    @Override
    public Mono<Void> publish(
            AuditEvent event) {

        return Mono.deferContextual(contextView -> {

            if (!contextView.hasKey(
                    AUDIT_CONTEXT_KEY
            )) {
                return Mono.empty();
            }

            AuditContext auditContext =
                    contextView.get(
                            AUDIT_CONTEXT_KEY
                    );

            collector.accept(
                    auditContext,
                    event
            );

            return Mono.empty();
        });
    }
}
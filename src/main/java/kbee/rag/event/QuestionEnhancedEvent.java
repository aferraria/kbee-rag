package kbee.rag.event;

import java.time.Duration;

import kbee.rag.audit.AuditEvent;
import kbee.rag.search.EnhancedQuestion;

public record QuestionEnhancedEvent(
        EnhancedQuestion question,
        Duration duration
) implements AuditEvent {
}
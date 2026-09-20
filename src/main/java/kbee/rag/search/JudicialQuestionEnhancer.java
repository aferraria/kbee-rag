package kbee.rag.search;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Service;

import kbee.rag.audit.AuditPublisher;
import kbee.rag.event.QuestionEnhancedEvent;
import kbee.rag.text.LegalTextEnhancer;
import reactor.core.publisher.Mono;

@Service
public class JudicialQuestionEnhancer
        implements QuestionEnhancer {


    private final LegalTextEnhancer legalTextEnhancer;

    private final AuditPublisher auditPublisher;

    public JudicialQuestionEnhancer(
            LegalTextEnhancer legalTextEnhancer,
            AuditPublisher auditPublisher) {

        this.legalTextEnhancer =
                legalTextEnhancer;

        this.auditPublisher =
                auditPublisher;
    }
    
    @Override
    public Mono<EnhancedQuestion> enhance(
            String question) {

        long start =
                System.nanoTime();

        return doEnhance(question)
                .flatMap(enhancedQuestion -> {

                    Duration duration =
                            Duration.ofNanos(
                                    System.nanoTime() - start
                            );

                    return auditPublisher
                            .publish(
                                    new QuestionEnhancedEvent(
                                            enhancedQuestion,
                                            duration
                                    )
                            )
                            .thenReturn(enhancedQuestion);
                });
    }

    public Mono<EnhancedQuestion> doEnhance(
            String question) {

        if (question == null
                || question.isBlank()) {

            return Mono.just(
                    new EnhancedQuestion(
                            question,
                            null,
                            List.of(),
                            List.of()
                    )
            );
        }

        return legalTextEnhancer
                .enhance(
                        question
                )
                .map(enhancement ->
                        new EnhancedQuestion(
                                question,
                                enhancement.text() == null
                                        || enhancement.text().isBlank()
                                                ? null
                                                : enhancement.text(),
                                enhancement.voices(),
                                enhancement.propositions()
                        )
                );
    }
}
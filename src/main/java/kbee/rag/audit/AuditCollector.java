package kbee.rag.audit;

import org.springframework.stereotype.Component;

import kbee.rag.event.QuestionEnhancedEvent;

@Component
public class AuditCollector {

    public void accept(
            AuditContext context,
            AuditEvent event) {

        switch (event) {
//
            case QuestionEnhancedEvent e -> {
                context.enhancedQuestion(
                        e.question()
                );

                context.questionEnhancementDuration(
                        e.duration()
                );
            }
//
//            case SearchCompletedEvent e -> {
//                context.searchResults(
//                        e.results()
//                );
//
//                context.searchDuration(
//                        e.duration()
//                );
//            }
//
//            case SourcesExpandedEvent e -> {
//                context.expandedSources(
//                        e.sources()
//                );
//
//                context.sourceExpansionDuration(
//                        e.duration()
//                );
//            }
//
//            case RerankCompletedEvent e -> {
//                context.rerankedSources(
//                        e.sources()
//                );
//
//                context.rerankDuration(
//                        e.duration()
//                );
//            }
//
//            case AnswerGeneratedEvent e -> {
//                context.answer(
//                        e.answer()
//                );
//
//                context.answerGenerationDuration(
//                        e.duration()
//                );
//            }
//
            default -> {
            }
        }
    }
}
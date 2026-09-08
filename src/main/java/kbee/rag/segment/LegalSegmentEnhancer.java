package kbee.rag.segment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import kbee.rag.text.LegalTextEnhancer;
import reactor.core.publisher.Mono;

@Service
public class LegalSegmentEnhancer
        implements SegmentEnhancer {

    private final LegalTextEnhancer legalTextEnhancer;

    private final String segmentPromptName;

    public LegalSegmentEnhancer(
            LegalTextEnhancer legalTextEnhancer,
            @Value("${rag.prompts.enrichment.segment}")
            String segmentPromptName) {

        this.legalTextEnhancer =
                legalTextEnhancer;

        this.segmentPromptName =
                segmentPromptName;
    }

    @Override
    public Mono<TextSegment> enhance(
            TextSegment segment) {

        if (segment == null) {

            return Mono.error(
                    new IllegalArgumentException(
                            "segment must not be null"
                    )
            );
        }

        if (segment.text() == null
                || segment.text().isBlank()) {

            return Mono.just(
                    segment
            );
        }

        return legalTextEnhancer
                .enhance(
                        segment.text(),
                        segmentPromptName
                )
                .map(enhancement ->
                        new TextSegment(
                                segment.documentId(),
                                segment.documentTitle(),
                                segment.documentDate(),
                                segment.sectionId(),
                                segment.sectionTitle(),
                                segment.sectionPath(),
                                segment.segmentNumber(),
                                segment.sectionSegmentNumber(),
                                segment.text(),
                                enhancement.text() == null
                                        || enhancement.text().isBlank()
                                                ? null
                                                : enhancement.text(),
                                segment.documentType(),
                                enhancement.concepts(),
                                enhancement.propositions()
                        )
                );
    }
}
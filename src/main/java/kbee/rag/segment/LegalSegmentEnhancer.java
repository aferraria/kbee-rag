package kbee.rag.segment;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import kbee.rag.text.LegalEnhancement;
import kbee.rag.text.LegalTextEnhancer;
import kbee.rag.text.TextEnhanced;
import reactor.core.publisher.Mono;

@Service
public class LegalSegmentEnhancer
        implements SegmentEnhancer {


    private final LegalTextEnhancer legalTextEnhancer;

    private final String segmentPromptName;

    private final String segmentBatchPromptName;

    public LegalSegmentEnhancer(
            LegalTextEnhancer legalTextEnhancer,

            @Value("${rag.prompts.enrichment.segment}")
            String segmentPromptName,

            @Value("${rag.prompts.enrichment.segment-batch}")
            String segmentBatchPromptName) {

        this.legalTextEnhancer =
                legalTextEnhancer;

        this.segmentPromptName =
                segmentPromptName;

        this.segmentBatchPromptName =
                segmentBatchPromptName;
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
                        segment.text()
                )
                .map(enhancement ->
                        applyEnhancement(
                                segment,
                                enhancement
                        )
                );
    }

    @Override
    public Mono<List<TextSegment>> enhance(
            List<TextSegment> segments) {

        if (segments == null
                || segments.isEmpty()) {

            return Mono.just(
                    List.of()
            );
        }

        List<TextSegment> validSegments =
                segments.stream()
                        .filter(segment ->
                                segment != null
                                        && segment.text() != null
                                        && !segment.text().isBlank()
                        )
                        .toList();

        /*
         * Caso raro: todos vacíos.
         */
        if (validSegments.isEmpty()) {

            return Mono.just(
                    List.copyOf(
                            segments
                    )
            );
        }

        /*
         * Importante:
         *
         * Si todos los segmentos del batch tienen texto,
         * podemos hacer un mapeo posicional directo.
         */
        if (validSegments.size()
                == segments.size()) {

            List<String> texts =
                    segments.stream()
                            .map(TextSegment::text)
                            .toList();

            return legalTextEnhancer
                    .enhance(
                            texts
                    )
                    .map(enhancements -> {

                        if (enhancements.size()
                                != segments.size()) {

                            throw new IllegalStateException(
                                    "Cantidad de enriquecimientos incorrecta. "
                                            + "Esperados="
                                            + segments.size()
                                            + ", recibidos="
                                            + enhancements.size()
                            );
                        }

                        List<TextSegment> result =
                                new ArrayList<>(
                                        segments.size()
                                );

                        for (int i = 0;
                                i < segments.size();
                                i++) {

                            result.add(
                                    applyEnhancement(
                                            segments.get(i),
                                            enhancements.get(i)
                                    )
                            );
                        }

                        return List.copyOf(
                                result
                        );
                    });
        }

        /*
         * Si hay segmentos vacíos dentro del batch,
         * enriquecemos solamente los válidos y luego
         * reconstruimos el orden original.
         */
        List<String> texts =
                validSegments.stream()
                        .map(TextSegment::text)
                        .toList();

        return legalTextEnhancer
                .enhance(
                        texts
                )
                .map(enhancements -> {

                    if (enhancements.size()
                            != validSegments.size()) {

                        throw new IllegalStateException(
                                "Cantidad de enriquecimientos incorrecta. "
                                        + "Esperados="
                                        + validSegments.size()
                                        + ", recibidos="
                                        + enhancements.size()
                        );
                    }

                    List<TextSegment> result =
                            new ArrayList<>(
                                    segments.size()
                            );

                    int enhancementIndex = 0;

                    for (TextSegment segment :
                            segments) {

                        if (segment == null) {

                            throw new IllegalArgumentException(
                                    "El batch contiene un segmento null"
                            );
                        }

                        if (segment.text() == null
                                || segment.text().isBlank()) {

                            result.add(
                                    segment
                            );

                            continue;
                        }

                        result.add(
                                applyEnhancement(
                                        segment,
                                        enhancements.get(
                                                enhancementIndex++
                                        )
                                )
                        );
                    }

                    return List.copyOf(
                            result
                    );
                });
    }

    private TextSegment applyEnhancement(
            TextSegment segment,
            TextEnhanced enhancement) {

        return new TextSegment(
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
                enhancement.voices(),
                enhancement.propositions(),
                segment.metainfo()
        );
    }
}
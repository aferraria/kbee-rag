package kbee.rag.document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import kbee.rag.search.ExpandedSource;
import kbee.rag.search.SegmentDao;
import kbee.rag.search.SegmentSearchResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class JudicialDocumentDao implements DocumentDao {

    private static final int MAX_HITS_PER_DOCUMENT =
            3;

    private static final int OVERLAP_DISTANCE =
            2;

    private static final int EXPANSION_RADIUS =
            1;
    
    private static final int LOCAL_TOP_K = 3;


    private final SegmentDao segmentDao;

    public JudicialDocumentDao(
            SegmentDao solrService) {

        this.segmentDao =
                solrService;
    }
    
    @Override
    public Mono<List<ExpandedSource>> getSources(
            List<SegmentSearchResult> results,
            String question) {

        if (results == null
                || results.isEmpty()) {

            return Mono.just(
                    List.of()
            );
        }

        Map<String, List<SegmentSearchResult>> candidatesByDocument =
                groupByDocument(
                        results
                );

        return Flux.fromIterable(
                candidatesByDocument.entrySet()
        )
        .concatMap(entry ->
                buildExpandedSource(
                        entry.getKey(),
                        entry.getValue(),
                        question
                )
        )
        .collectList();
    }

    public Mono<List<ExpandedSource>> getSources2(
            List<SegmentSearchResult> results,
            String question) {

        if (results == null
                || results.isEmpty()) {

            return Mono.just(
                    List.of()
            );
        }

        Map<String, List<SegmentSearchResult>> candidatesByDocument =
                groupByDocument(
                        results
                );

        return Flux.fromIterable(
                candidatesByDocument.entrySet()
        )
        .concatMap(entry -> {

            List<SegmentSearchResult> parts =
                    entry.getValue()
                            .stream()
                            .sorted(
                                    Comparator
                                            .comparing(
                                                    SegmentSearchResult::score
                                            )
                                            .reversed()
                            )
                            .toList();

            if (parts.isEmpty()) {
                return Mono.<ExpandedSource>empty();
            }

            List<SegmentSearchResult> selectedParts =
                    selectNonOverlappingParts(
                            parts
                    );

            if (selectedParts.isEmpty()) {
                return Mono.<ExpandedSource>empty();
            }

            SegmentSearchResult selected =
                    selectedParts.get(0);

            return expandNeighbors(
                    selectedParts,
                    EXPANSION_RADIUS,
                    question
            )
            .collectList()
            .map(expandedParts -> {

                Map<String, SegmentSearchResult> contextById =
                        new LinkedHashMap<>();

                for (ExpandedSource expandedPart :
                        expandedParts) {

                    SegmentSearchResult partSelected =
                            expandedPart.selected();

                    if (partSelected != null) {
                        contextById.putIfAbsent(
                                partSelected.id(),
                                partSelected
                        );
                    }

                    for (SegmentSearchResult context :
                            expandedPart.contextSegments()) {

                        contextById.putIfAbsent(
                                context.id(),
                                context
                        );
                    }
                }

                contextById.remove(
                        selected.id()
                );

                List<SegmentSearchResult> contextSegments =
                        new ArrayList<>(
                                contextById.values()
                        );

                return new ExpandedSource(
                        selected,
                        contextSegments
                );
            });
        })
        .collectList();
    }
    
    
    
    public String getDocumentId(ExpandedSource source) {
    	String sourceId = source.selected().documentId();
    	String s[] = sourceId.split("-");
    	return s.length == 2
    		? s[1]
    		: s[2];		
    }
    
    
    @Override
    public Mono<DocumentText> getDocument(
            String documentId) {

        return segmentDao
                .findDocumentSegments(
                        documentId
                )
                .sort(
                        Comparator.comparingInt(
                                SegmentSearchResult::segmentNumber
                        )
                )
                .collectList()
                .flatMap(segments -> {

                    if (segments.isEmpty()) {
                        return Mono.empty();
                    }

                    SegmentSearchResult first =
                            segments.get(0);

                    String text =
                            segments.stream()
                                    .map(
                                            SegmentSearchResult::text
                                    )
                                    .filter(
                                            Objects::nonNull
                                    )
                                    .filter(value ->
                                            !value.isBlank()
                                    )
                                    .collect(
                                            Collectors.joining(
                                                    "\n\n"
                                            )
                                    );

                    return Mono.just(
                            new DocumentText(
                                    first.documentId(),
                                    first.documentTitle(),
                                    text
                            )
                    );
                });
    }
    
    private Mono<ExpandedSource> buildExpandedSource(
            String documentId,
            List<SegmentSearchResult> documentResults,
            String question) {

        List<SegmentSearchResult> parts =
                documentResults.stream()
                        .sorted(
                                Comparator
                                        .comparing(
                                                SegmentSearchResult::score
                                        )
                                        .reversed()
                        )
                        .toList();

        if (parts.isEmpty()) {
            return Mono.empty();
        }

        List<SegmentSearchResult> selectedParts =
                selectNonOverlappingParts(
                        parts
                );

        if (selectedParts.isEmpty()) {
            return Mono.empty();
        }

        SegmentSearchResult selected =
                selectedParts.get(0);

        return segmentDao
                .findDocumentSegments(
                        documentId,
                        question,
                        LOCAL_TOP_K
                )
                .collectList()
                .flatMap(localSegments -> {

                    /*
                     * Unimos hits globales + hits locales.
                     */
                    Map<String, SegmentSearchResult> evidenceById =
                            new LinkedHashMap<>();

                    for (SegmentSearchResult part :
                            selectedParts) {

                        evidenceById.putIfAbsent(
                                part.id(),
                                part
                        );
                    }

                    for (SegmentSearchResult local :
                            localSegments) {

                        evidenceById.putIfAbsent(
                                local.id(),
                                local
                        );
                    }

                    List<SegmentSearchResult> evidence =
                            new ArrayList<>(
                                    evidenceById.values()
                            );

                    /*
                     * Ahora sólo expandimos ±1 alrededor
                     * de evidencia realmente relevante.
                     */
                    return expandNeighbors(
                            evidence,
                            EXPANSION_RADIUS,
                            question
                    )
                    .collectList();
                })
                .map(expandedParts -> {

                    Map<String, SegmentSearchResult> contextById =
                            new LinkedHashMap<>();

                    for (ExpandedSource expandedPart :
                            expandedParts) {

                        SegmentSearchResult partSelected =
                                expandedPart.selected();

                        if (partSelected != null) {

                            contextById.putIfAbsent(
                                    partSelected.id(),
                                    partSelected
                            );
                        }

                        for (SegmentSearchResult context :
                                expandedPart.contextSegments()) {

                            contextById.putIfAbsent(
                                    context.id(),
                                    context
                            );
                        }
                    }

                    contextById.remove(
                            selected.id()
                    );

                    return new ExpandedSource(
                            selected,
                            new ArrayList<>(
                                    contextById.values()
                            )
                    );
                });
    }
    /*
     * =================================================
     * GROUP BY DOCUMENT
     * =================================================
     */

    private Map<String, List<SegmentSearchResult>> groupByDocument(
            List<SegmentSearchResult> results) {

        return results.stream()
                .filter(Objects::nonNull)
                .collect(
                        Collectors.groupingBy(
                                this::getLogicalDocumentId,
                                LinkedHashMap::new,
                                Collectors.toList()
                        )
                );
    }

    /*
     * =================================================
     * LOGICAL DOCUMENT
     * =================================================
     */

    private String getLogicalDocumentId(
            SegmentSearchResult result) {

        String documentId =
                result.documentId();

        if (documentId == null) {
            return null;
        }

        if (documentId.startsWith(
                "sumario-fallo-"
        )) {

            String value =
                    documentId.substring(
                            "sumario-".length()
                    );

            int lastDash =
                    value.lastIndexOf('-');

            if (lastDash > 0) {
                return value.substring(
                        0,
                        lastDash
                );
            }
        }

        return documentId;
    }

    /*
     * =================================================
     * SELECT PARTS
     * =================================================
     */

    private List<SegmentSearchResult> selectNonOverlappingParts(
            List<SegmentSearchResult> parts) {

        List<SegmentSearchResult> selected =
                new ArrayList<>();

        for (SegmentSearchResult part :
                parts) {

            boolean overlaps =
                    selected.stream()
                            .anyMatch(existing ->
                                    overlaps(
                                            existing,
                                            part
                                    )
                            );

            if (!overlaps) {

                selected.add(
                        part
                );
            }

            if (selected.size()
                    >= MAX_HITS_PER_DOCUMENT) {

                break;
            }
        }

        return selected;
    }

    /*
     * =================================================
     * OVERLAP
     * =================================================
     */

    private boolean overlaps(
            SegmentSearchResult existing,
            SegmentSearchResult candidate) {

        /*
         * Segmentos provenientes de documentos físicos
         * distintos no se consideran solapados.
         *
         * Por ejemplo:
         * sumario vs fallo.
         */
        if (!Objects.equals(
                existing.documentId(),
                candidate.documentId()
        )) {

            return false;
        }

        return Math.abs(
                existing.segmentNumber()
                        - candidate.segmentNumber()
        ) <= OVERLAP_DISTANCE;
    }

    /*
     * =================================================
     * EXPANSION
     * =================================================
     */
    
    private Flux<ExpandedSource> expandNeighbors(
            List<SegmentSearchResult> selectedParts,
            int radius,
            String question) {

        return Flux.fromIterable(selectedParts)
                .concatMap(selected ->
                        expandNeighbors(
                                selected,
                                radius,
                                question
                        )
                );
    }

    private Mono<ExpandedSource> expandNeighbors(
            SegmentSearchResult selected,
            int radius,
            String question) {

        return findNeighborSegments(
                selected,
                radius
        )
        .collectList()
        .map(context ->
                new ExpandedSource(
                        selected,
                        context
                )
        );
    }


    /*
     * =================================================
     * FIND NEIGHBORS
     * =================================================
     */

    private Flux<SegmentSearchResult> findNeighborSegments(
            SegmentSearchResult selected,
            int radius) {

        int from =
                Math.max(
                        0,
                        selected.segmentNumber() - radius
                );

        int to =
                selected.segmentNumber() + radius;

        return segmentDao.findSegments(
                selected.documentId(),
                from,
                to
        );
    }
}
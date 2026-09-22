package kbee.rag.search;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import kbee.rag.ollama.OllamaLlmService;
import kbee.rag.segment.EmbeddedSegment;
import kbee.rag.segment.TextSegment;
import kbee.rag.thesaurus.Concept;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class SolrSegmentDao
        implements SegmentDao {

    private static final Logger log =
            LoggerFactory.getLogger(SolrSegmentDao.class);;
            
    private final SolrClient solrClient;

    private final String segmentCore;

    public SolrSegmentDao(
            SolrClient solrClient,
            @Value("${solr.target.core}")
            String segmentCore) {

        this.solrClient =
                solrClient;

        this.segmentCore =
                segmentCore;
    }

    /*
     * =================================================
     * ADD
     * =================================================
     */

    @Override
    public Mono<Void> add(
            EmbeddedSegment embeddedSegment) {

        if (embeddedSegment == null) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {

                    long start =
                            System.nanoTime();

                    try {

                        SolrInputDocument document =
                                toSolrDocument(
                                        embeddedSegment
                                );

                        solrClient.add(
                                segmentCore,
                                document
                        );

                        long elapsedMs =
                                (System.nanoTime() - start)
                                        / 1_000_000;

                        log.info(
                                "SOLR ADD | segment={} | elapsedMs={}",
                                embeddedSegment.segment()
                                        .documentId(),
                                elapsedMs
                        );

                    } catch (Exception e) {

                        throw new IllegalStateException(
                                "Error indexando segmento en Solr",
                                e
                        );
                    }

                })
                .subscribeOn(
                        Schedulers.boundedElastic()
                )
                .then();
    }

    /*
     * =================================================
     * DELETE DOCUMENT
     * =================================================
     */

    @Override
    public Mono<Void> deleteByDocumentId(
            String documentId) {

        if (documentId == null
                || documentId.isBlank()) {

            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {

                    try {

                        solrClient.deleteByQuery(
                                segmentCore,
                                "document_id:\""
                                        + escape(documentId)
                                        + "\""
                        );

                    } catch (Exception e) {

                        throw new IllegalStateException(
                                "Error eliminando segmentos del documento "
                                        + documentId,
                                e
                        );
                    }
                })
                .subscribeOn(
                        Schedulers.boundedElastic()
                )
                .then();
    }
    
    @Override
    public Flux<SegmentSearchResult> findDocumentSegments(
            String documentId) {

        if (documentId == null
                || documentId.isBlank()) {

            return Flux.empty();
        }

        ModifiableSolrParams params =
                new ModifiableSolrParams();

        params.set(
                "q",
                "document_id:\""
                        + escape(documentId)
                        + "\""
        );

        params.set(
                "sort",
                "segment_number asc"
        );

        /*
         * Queremos todos los segmentos del documento.
         *
         * Para jurisprudencia no debería acercarse
         * ni remotamente a este límite.
         */
        params.set(
                "rows",
                10000
        );

        return search(
                params
        );
    }
    
    
    @Override
    public Flux<SegmentSearchResult> findDocumentSegments(
            String documentId,
            String text,
            int topK) {

        if (documentId == null
                || documentId.isBlank()
                || text == null
                || text.isBlank()) {

            return Flux.empty();
        }

        ModifiableSolrParams params =
                new ModifiableSolrParams();

        params.set(
                "q",
                ClientUtils.escapeQueryChars(text)
        );

        params.set(
                "df",
                "segment_text"
        );

        params.set(
                "q.op",
                "OR"
        );

        params.add(
                "fq",
                "document_id:\""
                        + ClientUtils.escapeQueryChars(
                                documentId
                        )
                        + "\""
        );

        params.add(
                "fq",
                "-section_id:RESUELVE"
        );

        params.set(
                "rows",
                topK
        );

        return search(params);
    }
    /*
     * =================================================
     * COMMIT
     * =================================================
     */

    @Override
    public Mono<Void> commit() {

        return Mono.fromRunnable(() -> {

                    try {

                        solrClient.commit(
                                segmentCore
                        );

                    } catch (Exception e) {

                        throw new IllegalStateException(
                                "Error haciendo commit en Solr",
                                e
                        );
                    }
                })
                .subscribeOn(
                        Schedulers.boundedElastic()
                )
                .then();
    }

    /*
     * =================================================
     * SEARCH
     * =================================================
     */

    @Override
    public Flux<SegmentSearchResult> search(
            SolrParams params) {

        ModifiableSolrParams effectiveParams =
                new ModifiableSolrParams(
                        params
                );

        if (effectiveParams.get("fl") == null) {

            effectiveParams.set(
                    "fl",
                    "id,"
                            + "document_id,"
                            + "document_title,"
                            + "document_date,"
                            + "section_id,"
                            + "section_title,"
                            + "section_path,"
                            + "segment_number,"
                            + "section_segment_number,"
                            + "segment_text,"
                            + "score"
            );
        }

        return Mono.fromCallable(() -> {

                    QueryRequest request =
                            new QueryRequest(
                                    effectiveParams,
                                    SolrRequest.METHOD.POST
                            );

                    QueryResponse response =
                            request.process(
                                    solrClient,
                                    segmentCore
                            );

                    return response;
                })
                .subscribeOn(
                        Schedulers.boundedElastic()
                )
                .flatMapMany(response ->
                        Flux.fromIterable(
                                response.getResults()
                        )
                )
                .map(
                        this::toSegmentSearchResult
                )
                .doOnError(
                        Throwable::printStackTrace
                );
    }

    /*
     * =================================================
     * FIND SEGMENTS
     * =================================================
     */

    @Override
    public Flux<SegmentSearchResult> findSegments(
            String documentId,
            int from,
            int to) {

        ModifiableSolrParams params =
                new ModifiableSolrParams();

        params.set(
                "q",
                "document_id:\""
                        + escape(documentId)
                        + "\""
        );

        params.add(
                "fq",
                "segment_number:["
                        + from
                        + " TO "
                        + to
                        + "]"
        );

        params.set(
                "sort",
                "segment_number asc"
        );

        params.set(
                "rows",
                Math.max(
                        0,
                        to - from + 1
                )
        );

        return search(
                params
        );
    }
    
    private String buildSegmentId(
            TextSegment segment) {

        return segment.documentId()
                + ":"
                + segment.segmentNumber();
    }

    private SolrInputDocument toSolrDocument(
            EmbeddedSegment embeddedSegment) {

        TextSegment segment =
                embeddedSegment.segment();

        SolrInputDocument document =
                new SolrInputDocument();

        document.addField(
                "id",
                buildSegmentId(segment)
        );

        document.addField(
                "document_id",
                segment.documentId()
        );

        if (segment.documentTitle() != null
                && !segment.documentTitle().isBlank()) {

            document.addField(
                    "document_title",
                    segment.documentTitle()
            );
        }

        if (segment.documentDate() != null) {

            document.addField(
                    "document_date",
                    segment.documentDate()
                            .toInstant()
                            .toString()
            );
        }

        if (segment.documentType() != null
                && !segment.documentType().isBlank()) {

            document.addField(
                    "document_type",
                    segment.documentType()
            );
        }

        if (segment.sectionId() != null
                && !segment.sectionId().isBlank()) {

            document.addField(
                    "section_id",
                    segment.sectionId()
            );
        }

        if (segment.sectionTitle() != null
                && !segment.sectionTitle().isBlank()) {

            document.addField(
                    "section_title",
                    segment.sectionTitle()
            );
        }

        if (segment.sectionPath() != null
                && !segment.sectionPath().isBlank()) {

            document.addField(
                    "section_path",
                    segment.sectionPath()
            );
        }

        document.addField(
                "segment_number",
                segment.segmentNumber()
        );

        document.addField(
                "section_segment_number",
                segment.sectionSegmentNumber()
        );

        document.addField(
                "segment_text",
                segment.text()
        );

        if (embeddedSegment.embedding() != null
                && !embeddedSegment.embedding().isEmpty()) {

            document.addField(
                    "embedding",
                    embeddedSegment.embedding()
            );
        }

        if (embeddedSegment.legalEmbedding() != null
                && !embeddedSegment.legalEmbedding().isEmpty()) {

            document.addField(
                    "legal_embedding",
                    embeddedSegment.legalEmbedding()
            );
        }

        List<String> terms =
                segment.concepts()
                        .stream()
                        .filter(Objects::nonNull)
                        .map(Concept::term)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(term ->
                                !term.isBlank()
                        )
                        .distinct()
                        .toList();

        if (!terms.isEmpty()) {

            document.addField(
                    "thesaurus_term",
                    terms
            );
        }
        
        List<String> subjects =
                segment.metainfo()
                        .get("subjects") instanceof List<?> list
                                ? list.stream()
                                        .filter(String.class::isInstance)
                                        .map(String.class::cast)
                                        .collect(Collectors.toCollection(ArrayList::new))
                                : new ArrayList<>();

        if (!subjects.isEmpty()) {

            document.addField(
                    "subjects",
                    subjects
            );
        }

        List<String> propositions =
                segment.propositions()
                        .stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(proposition ->
                                !proposition.isBlank()
                        )
                        .distinct()
                        .toList();

        if (!propositions.isEmpty()) {

            document.addField(
                    "legal_proposition",
                    propositions
            );
        }

        return document;
    }
    /*
     * =================================================
     * RESULT MAPPING
     * =================================================
     */

    private SegmentSearchResult toSegmentSearchResult(
            SolrDocument document) {

        return new SegmentSearchResult(
                stringValue(
                        document,
                        "id"
                ),
                stringValue(
                        document,
                        "document_id"
                ),
                stringValue(
                        document,
                        "document_title"
                ),
                dateValue(
                        document,
                        "document_date"
                ),
                stringValue(
                        document,
                        "section_id"
                ),
                stringValue(
                        document,
                        "section_title"
                ),
                stringValue(
                        document,
                        "section_path"
                ),
                integerValue(
                        document,
                        "segment_number"
                ),
                integerValue(
                        document,
                        "section_segment_number"
                ),
                stringValue(
                        document,
                        "segment_text"
                ),
                floatValue(
                        document,
                        "score"
                )
        );
    }

    /*
     * =================================================
     * STRING VALUE
     * =================================================
     */

    private String stringValue(
            SolrDocument document,
            String field) {

        Object value =
                document.getFieldValue(
                        field
                );

        if (value == null) {
            return null;
        }

        if (value instanceof Collection<?> collection) {

            if (collection.isEmpty()) {
                return null;
            }

            Object first =
                    collection.iterator()
                            .next();

            return first == null
                    ? null
                    : first.toString();
        }

        return value.toString();
    }

    /*
     * =================================================
     * INTEGER VALUE
     * =================================================
     */

    private Integer integerValue(
            SolrDocument document,
            String field) {

        Object value =
                document.getFieldValue(
                        field
                );

        if (value == null) {
            return null;
        }

        if (value instanceof Integer integer) {
            return integer;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value instanceof Collection<?> collection) {

            if (collection.isEmpty()) {
                return null;
            }

            Object first =
                    collection.iterator()
                            .next();

            if (first == null) {
                return null;
            }

            if (first instanceof Number number) {
                return number.intValue();
            }

            return Integer.valueOf(
                    first.toString()
            );
        }

        return Integer.valueOf(
                value.toString()
        );
    }

    /*
     * =================================================
     * FLOAT VALUE
     * =================================================
     */

    private Float floatValue(
            SolrDocument document,
            String field) {

        Object value =
                document.getFieldValue(
                        field
                );

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.floatValue();
        }

        return Float.valueOf(
                value.toString()
        );
    }

    /*
     * =================================================
     * DATE VALUE
     * =================================================
     */

    private OffsetDateTime dateValue(
            SolrDocument document,
            String field) {

        Object value =
                document.getFieldValue(
                        field
                );

        if (value == null) {
            return null;
        }

        if (value instanceof OffsetDateTime odt) {
            return odt;
        }

        if (value instanceof Instant instant) {

            return instant.atOffset(
                    ZoneOffset.UTC
            );
        }

        if (value instanceof java.util.Date date) {

            return date.toInstant()
                    .atOffset(
                            ZoneOffset.UTC
                    );
        }

        String text =
                value.toString()
                        .trim();

        try {

            return OffsetDateTime.parse(
                    text
            );

        } catch (DateTimeParseException ignored) {
        }

        DateTimeFormatter legacyFormatter =
                DateTimeFormatter.ofPattern(
                        "EEE MMM dd HH:mm:ss 'GMT'XXX yyyy",
                        Locale.ENGLISH
                );

        try {

            return OffsetDateTime.parse(
                    text,
                    legacyFormatter
            );

        } catch (DateTimeParseException e) {

            throw new IllegalArgumentException(
                    "No se pudo convertir el campo "
                            + field
                            + " a OffsetDateTime: "
                            + text
                            + " (tipo="
                            + value.getClass().getName()
                            + ")",
                    e
            );
        }
    }

    /*
     * =================================================
     * ESCAPE
     * =================================================
     */

    private String escape(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                );
    }
}
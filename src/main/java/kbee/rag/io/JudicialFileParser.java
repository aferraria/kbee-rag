package kbee.rag.io;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class JudicialFileParser
        implements ApiFileParser {

    /*
     * =================================================
     * CONSTANTES
     * =================================================
     */

    private static final String START_MARKER =
            "Texto del fallo:";

    private static final String END_MARKER =
            "Sumarios del fallo";

    private static final String NOT_FOUND_TEXT =
            "El fallo solicitado no existe.Volver";

    /*
     * =================================================
     * METADATA
     * =================================================
     */

    public static final String META_DOCUMENT_ID =
            "documentId";

    public static final String META_DOCUMENT_TYPE =
            "documentType";

    public static final String META_DOCUMENT_TITLE =
            "documentTitle";

    public static final String META_DOCUMENT_DATE =
            "documentDate";

    public static final String META_FALLO_ID =
            "falloId";

    public static final String META_SUMMARY_NUMBER =
            "summaryNumber";

    public static final String META_THESAURUS_TERMS =
            "thesaurusTerms";
    
    public static final String META_SUBJECTS =
            "subjects";

    /*
     * =================================================
     * RECORD INTERNO
     * =================================================
     */

    private record SummaryData(
            List<String> subjects,
            List<String> thesaurusTerms,
            String text) {
    }
    /*
     * =================================================
     * API
     * =================================================
     */

    @Override
    public Flux<TextFile> parse(
            ApiFile file) {

        if (file == null) {
            return Flux.error(
                    new IllegalArgumentException(
                            "file no puede ser null"
                    )
            );
        }

        return readFile(file)
                .flatMapMany(content ->
                        parseContent(
                                file,
                                content
                        )
                );
    }

    /*
     * =================================================
     * LECTURA
     * =================================================
     */

    private Mono<String> readFile(
            ApiFile file) {

        return Mono.fromCallable(() -> {

                    try (InputStream input =
                            file.openStream()) {

                        return new String(
                                input.readAllBytes(),
                                StandardCharsets.UTF_8
                        );
                    }
                })
                .subscribeOn(
                        Schedulers.boundedElastic()
                );
    }

    /*
     * =================================================
     * PARSEO GENERAL
     * =================================================
     */

    private Flux<TextFile> parseContent(
            ApiFile file,
            String content) {

        if (isMissingDecision(content)) {

            System.out.println(
                    file.name()
                            + " -> descartado: "
                            + "fallo inexistente"
            );

            return Flux.empty();
        }

        String falloId =
                buildDocumentId(
                        file.name()
                );

        String documentTitle =
                extractTitle(
                        content
                );

        OffsetDateTime documentDate =
                extractDate(
                        content
                );

        String decisionText =
                extractDecisionText(
                        content
                );

        List<TextFile> result =
                new ArrayList<>();

        /*
         * Fallo.
         */
        
        TextFile decision = null;
        
        if (decisionText != null
                && !decisionText.isBlank()) {
        	
        	decision = buildDecision(
                    falloId,
                    documentTitle,
                    documentDate,
                    decisionText
            ); 

        }

        /*
         * Sumarios.
         */
        
        Set<String> subjects = new HashSet<>();

        List<SummaryData> summaries =
                extractSummaries(
                        content
                );

        for (int i = 0;
                i < summaries.size();
                i++) {

            int summaryNumber =
                    i + 1;

            SummaryData summary =
                    summaries.get(i);
            
            subjects.addAll(summary.subjects());

            if (summary == null
                    || summary.text() == null
                    || summary.text().isBlank()) {

                continue;
            }

            result.add(
                    buildSummary(
                            falloId,
                            documentTitle,
                            documentDate,
                            summaryNumber,
                            summary
                    )
            );
        }
        
        if (decision != null) {

            Map<String, Object> metadata =
                    new LinkedHashMap<>(
                            decision.metadata()
                    );

            metadata.put(
                    META_SUBJECTS,
                    List.copyOf(subjects)
            );

            decision =
                    new TextFile(
                            decision.id(),
                            decision.name(),
                            decision.title(),
                            decision.type(),
                            decision.date(),
                            decision.text(),
                            metadata
                    );
            result.add(decision);
        }
        
        System.out.println(
                file.name()
                        + " -> falloId="
                        + falloId
                        + ", documentos="
                        + result.size()
                        + ", sumarios="
                        + summaries.size()
        );

        return Flux.fromIterable(
                result
        );
    }

    /*
     * =================================================
     * TEXTFILE - FALLO
     * =================================================
     */

    private TextFile buildDecision(
            String documentId,
            String documentTitle,
            OffsetDateTime documentDate,
            String text) {

        return new TextFile(
                documentId,
                documentId,
                documentTitle,
                "fallo",
                documentDate,
                text
        );
    }

    /*
     * =================================================
     * TEXTFILE - SUMARIO
     * =================================================
     */

    private TextFile buildSummary(
            String falloId,
            String documentTitle,
            OffsetDateTime documentDate,
            int summaryNumber,
            SummaryData summary) {

        String documentId =
                "sumario-"
                        + falloId
                        + "-"
                        + summaryNumber;

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                META_FALLO_ID,
                falloId
        );

        metadata.put(
                META_SUMMARY_NUMBER,
                summaryNumber
        );

        if (summary.thesaurusTerms() != null
                && !summary.thesaurusTerms().isEmpty()) {

            metadata.put(
                    META_THESAURUS_TERMS,
                    summary.thesaurusTerms()
            );
        }
        
        if (summary.subjects() != null
                && !summary.subjects().isEmpty()) {

            metadata.put(
                    META_SUBJECTS,
                    summary.subjects()
            );
        }

        return new TextFile(
                documentId,
                documentId,
                documentTitle,
                "sumario",
                documentDate,
                summary.text(),
                metadata
        );
    }

    /*
     * =================================================
     * VALIDACIÓN
     * =================================================
     */

    private boolean isMissingDecision(
            String content) {

        if (content == null
                || content.isBlank()) {

            return true;
        }

        return content.contains(
                NOT_FOUND_TEXT
        );
    }

    /*
     * =================================================
     * TEXTO DEL FALLO
     * =================================================
     */

    private String extractDecisionText(
            String content) {

        int start =
                content.indexOf(
                        START_MARKER
                );

        if (start < 0) {

            throw new IllegalStateException(
                    "No se encontró el marcador \""
                            + START_MARKER
                            + "\""
            );
        }

        start +=
                START_MARKER.length();

        int end =
                content.indexOf(
                        END_MARKER,
                        start
                );

        /*
         * Puede haber fallos sin sumarios.
         */

        if (end < 0) {
            end =
                    content.length();
        }

        return content
                .substring(
                        start,
                        end
                )
                .trim();
    }

    /*
     * =================================================
     * TÍTULO
     * =================================================
     */

    private String extractTitle(
            String content) {

        if (content == null
                || content.isBlank()) {

            return null;
        }

        String startMarker =
                "Carátula:";

        String endMarker =
                "Fecha:";

        int start =
                content.indexOf(
                        startMarker
                );

        if (start < 0) {
            return null;
        }

        start +=
                startMarker.length();

        int end =
                content.indexOf(
                        endMarker,
                        start
                );

        if (end < 0) {
            return null;
        }

        String title =
                content
                        .substring(
                                start,
                                end
                        )
                        .trim();

        return title.isBlank()
                ? null
                : title;
    }

    /*
     * =================================================
     * FECHA
     * =================================================
     */

    private OffsetDateTime extractDate(
            String content) {

        if (content == null
                || content.isBlank()) {

            return null;
        }

        String startMarker =
                "Fecha:";

        String endMarker =
                "Tribunal:";

        int start =
                content.indexOf(
                        startMarker
                );

        if (start < 0) {
            return null;
        }

        start +=
                startMarker.length();

        int end =
                content.indexOf(
                        endMarker,
                        start
                );

        if (end < 0) {
            return null;
        }

        String dateText =
                content
                        .substring(
                                start,
                                end
                        )
                        .trim();

        if (dateText.isBlank()) {
            return null;
        }

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern(
                        "dd/MM/yyyy"
                );

        LocalDate date =
                LocalDate.parse(
                        dateText,
                        formatter
                );

        return date
                .atStartOfDay()
                .atOffset(
                        ZoneOffset.UTC
                );
    }

    /*
     * =================================================
     * SUMARIOS
     * =================================================
     */

    private List<SummaryData> extractSummaries(
            String content) {

        List<SummaryData> summaries =
                new ArrayList<>();

        if (content == null
                || content.isBlank()) {

            return summaries;
        }

        /*
         * Busca:
         *
         * Sumarios del fallo 3
         */

        Pattern headerPattern =
                Pattern.compile(
                        "(?i)Sumarios\\s+del\\s+fallo\\s+(\\d+)"
                );

        Matcher headerMatcher =
                headerPattern.matcher(
                        content
                );

        if (!headerMatcher.find()) {

            return summaries;
        }

        int expectedCount =
                Integer.parseInt(
                        headerMatcher.group(1)
                );

        /*
         * Conservamos saltos de línea.
         */

        String summariesText =
                content.substring(
                        headerMatcher.end()
                );

        /*
         * Footer general.
         */

        int copyrightPos =
                summariesText.indexOf(
                        "© Copyright"
                );

        if (copyrightPos >= 0) {

            summariesText =
                    summariesText.substring(
                            0,
                            copyrightPos
                    );
        }

        /*
         * Normalizamos CR/LF y NBSP,
         * pero no todo el whitespace.
         */

        summariesText =
                summariesText
                        .replace(
                                "\r\n",
                                "\n"
                        )
                        .replace(
                                '\r',
                                '\n'
                        )
                        .replace(
                                '\u00A0',
                                ' '
                        );

        /*
         * =================================================
         * INICIO DE CADA SUMARIO
         * =================================================
         *
         * Ejemplos:
         *
         * ADMINISTRATIVOTesauro >
         *
         * PROCESAL - ADMINISTRATIVOTesauro >
         *
         * CONSTITUCIONAL - PROCESALTesauro >
         *
         * No validamos aquí la estructura de la materia.
         * Solamente detectamos una línea que comienza
         * el sumario y llega hasta el primer "Tesauro >".
         */

        Pattern summaryStartPattern =
                Pattern.compile(
                        "(?im)^\\s*[^\\r\\n]+?\\s*Tesauro\\s*>"
                );

        Matcher summaryMatcher =
                summaryStartPattern.matcher(
                        summariesText
                );

        List<Integer> starts =
                new ArrayList<>();

        while (summaryMatcher.find()) {

            starts.add(
                    summaryMatcher.start()
            );

            System.out.println(
                    "Inicio de sumario encontrado: "
                            + summaryMatcher.group()
            );
        }

        System.out.println(
                "Sumarios esperados="
                        + expectedCount
                        + ", comienzos encontrados="
                        + starts.size()
        );

        /*
         * Cortamos cada bloque.
         */

        for (int i = 0;
                i < starts.size();
                i++) {

            int start =
                    starts.get(i);

            int end =
                    (i + 1 < starts.size())
                            ? starts.get(i + 1)
                            : summariesText.length();

            String block =
                    summariesText
                            .substring(
                                    start,
                                    end
                            )
                            .trim();

            if (block.isBlank()) {
                continue;
            }

            /*
             * Sacar jurisprudencia vinculada,
             * concordante y sumarios relacionados.
             */

            block =
                    trimRelatedContent(
                            block
                    );

            /*
             * Footer eventual.
             */

            int cerrarPos =
                    indexOfIgnoreCase(
                            block,
                            "Cerrar"
                    );

            if (cerrarPos >= 0) {

                block =
                        block
                                .substring(
                                        0,
                                        cerrarPos
                                )
                                .trim();
            }

            if (block.isBlank()) {
                continue;
            }

            SummaryData summary =
                    parseSummaryBlock(
                            block
                    );

            if (summary != null
                    && summary.text() != null
                    && !summary.text().isBlank()) {

                summaries.add(
                        summary
                );
            }

            /*
             * El documento informa cuántos
             * sumarios existen.
             */

            if (summaries.size()
                    >= expectedCount) {

                break;
            }
        }

        System.out.println(
                "Sumarios extraídos="
                        + summaries.size()
        );

        return summaries;
    }

    /*
     * =================================================
     * PARSEO DE SUMARIO
     * =================================================
     */

    private SummaryData parseSummaryBlock(
            String block) {

        if (block == null
                || block.isBlank()) {

            return null;
        }

        block =
                block
                        .replace(
                                "\r\n",
                                "\n"
                        )
                        .replace(
                                '\r',
                                '\n'
                        )
                        .replace(
                                '\u00A0',
                                ' '
                        )
                        .trim();

        /*
         * =================================================
         * MATERIAS
         * =================================================
         *
         * Ejemplos:
         *
         * ADMINISTRATIVO
         * PROCESAL - ADMINISTRATIVO
         * CONSTITUCIONAL - PROCESAL
         *
         * Cada término separado por "-"
         * representa una materia distinta.
         */

        Pattern subjectPattern =
                Pattern.compile(
                        "^\\s*("
                                + "[A-ZÁÉÍÓÚÑ]+"
                                + "(?:\\s*-\\s*[A-ZÁÉÍÓÚÑ]+)*"
                                + ")\\s*(?=Tesauro\\s*>)"
                );

        Matcher subjectMatcher =
                subjectPattern.matcher(
                        block
                );

        List<String> subjects =
                new ArrayList<>();

        if (subjectMatcher.find()) {

            String subjectText =
                    normalizeWhitespace(
                            subjectMatcher.group(1)
                    );

            String[] subjectParts =
                    subjectText.split(
                            "\\s*-\\s*"
                    );

            for (String subjectPart :
                    subjectParts) {

                String subject =
                        normalizeWhitespace(
                                subjectPart
                        );

                if (!subject.isBlank()) {

                    subjects.add(
                            subject
                    );
                }
            }

            block =
                    block
                            .substring(
                                    subjectMatcher.end()
                            )
                            .trim();
        }

        /*
         * =================================================
         * TESAURO
         * =================================================
         */

        Pattern tesauroPattern =
                Pattern.compile(
                        "(?i)Tesauro\\s*>\\s*"
                );

        Matcher tesauroMatcher =
                tesauroPattern.matcher(
                        block
                );

        List<Integer> starts =
                new ArrayList<>();

        List<Integer> contentStarts =
                new ArrayList<>();

        while (tesauroMatcher.find()) {

            starts.add(
                    tesauroMatcher.start()
            );

            contentStarts.add(
                    tesauroMatcher.end()
            );
        }

        if (starts.isEmpty()) {
            return null;
        }

        List<String> thesaurusTerms =
                new ArrayList<>();

        /*
         * Todas las voces que tienen otro
         * "Tesauro >" después son confiables.
         */

        for (int i = 0;
                i < starts.size() - 1;
                i++) {

            String term =
                    normalizeWhitespace(
                            block.substring(
                                    contentStarts.get(i),
                                    starts.get(i + 1)
                            )
                    );

            if (!term.isBlank()) {

                thesaurusTerms.add(
                        term
                );
            }
        }

        /*
         * =================================================
         * ÚLTIMA VOZ + TEXTO NARRATIVO
         * =================================================
         */

        int lastContentStart =
                contentStarts.get(
                        contentStarts.size() - 1
                );

        String tail =
                normalizeWhitespace(
                        block.substring(
                                lastContentStart
                        )
                );

        /*
         * Formatos posibles:
         *
         * VOZ.El examen...
         *
         * VOZ.Cabe juzgar...
         *
         * o cuando el texto plano pierde
         * el separador:
         *
         * VOZLa Cámara...
         */

        Pattern narrativePattern =
                Pattern.compile(
                        "\\.(?=[A-ZÁÉÍÓÚÑ][a-záéíóúñ])"
                                + "|"
                                + "(?<=[A-ZÁÉÍÓÚÑ])"
                                + "(?=[A-ZÁÉÍÓÚÑ][a-záéíóúñ])"
                );

        Matcher narrativeMatcher =
                narrativePattern.matcher(
                        tail
                );

        if (!narrativeMatcher.find()) {

            System.out.println(
                    "Bloque descartado: "
                            + "no se encontró texto narrativo"
            );

            System.out.println(
                    "TAIL="
                            + tail
            );

            return null;
        }

        int narrativeStart =
                narrativeMatcher.end();

        String summaryText =
                normalizeWhitespace(
                        tail.substring(
                                narrativeStart
                        )
                );

        if (summaryText.isBlank()) {
            return null;
        }

        /*
         * =================================================
         * ÚLTIMA VOZ
         * =================================================
         */

        String beforeNarrative =
                normalizeWhitespace(
                        tail.substring(
                                0,
                                narrativeMatcher.start()
                        )
                );

        beforeNarrative =
                beforeNarrative
                        .replaceFirst(
                                "\\.\\s*$",
                                ""
                        )
                        .trim();

        String lastTerm =
                extractSingleThesaurusTerm(
                        beforeNarrative
                );

        if (lastTerm != null
                && !lastTerm.isBlank()) {

            thesaurusTerms.add(
                    lastTerm
            );
        }

        return new SummaryData(
                List.copyOf(
                        subjects
                ),
                List.copyOf(
                        thesaurusTerms
                ),
                summaryText
        );
    }
    /*
     * =================================================
     * ÚNICA VOZ TESAURO
     * =================================================
     */

    private String extractSingleThesaurusTerm(
            String value) {

        if (value == null
                || value.isBlank()) {

            return null;
        }

        /*
         * No intentamos inferir una estructura
         * que el texto plano ya perdió.
         */

        return null;
    }

    /*
     * =================================================
     * CONTENIDO RELACIONADO
     * =================================================
     */

    private String trimRelatedContent(
            String block) {

        if (block == null
                || block.isBlank()) {

            return "";
        }

        String[] markers = {
                "Jurisprudencia Vinculada:",
                "Jurisprudencia Concordante",
                "Sumarios relacionados"
        };

        int cut =
                block.length();

        for (String marker : markers) {

            int pos =
                    indexOfIgnoreCase(
                            block,
                            marker
                    );

            if (pos >= 0
                    && pos < cut) {

                cut = pos;
            }
        }

        String result =
                block
                        .substring(
                                0,
                                cut
                        )
                        .trim();

        /*
         * Si queda:
         *
         * "... texto. ("
         *
         * quitamos el paréntesis huérfano.
         */

        result =
                result
                        .replaceFirst(
                                "\\(\\s*$",
                                ""
                        )
                        .trim();

        return result;
    }

    /*
     * =================================================
     * INDEX OF IGNORE CASE
     * =================================================
     */

    private int indexOfIgnoreCase(
            String text,
            String search) {

        if (text == null
                || search == null) {

            return -1;
        }

        return text
                .toLowerCase(
                        Locale.ROOT
                )
                .indexOf(
                        search.toLowerCase(
                                Locale.ROOT
                        )
                );
    }

    /*
     * =================================================
     * NORMALIZACIÓN
     * =================================================
     */

    private String normalizeWhitespace(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace(
                        '\u00A0',
                        ' '
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    /*
     * =================================================
     * DOCUMENT ID
     * =================================================
     *
     * fallo-55165.txt
     *
     *      ↓
     *
     * fallo-55165
     */

    private String buildDocumentId(
            String fileName) {

        if (fileName == null
                || fileName.isBlank()) {

            throw new IllegalArgumentException(
                    "El archivo no tiene nombre"
            );
        }

        /*
         * Por si name() incluye algún path.
         */

        String normalized =
                fileName.replace(
                        '\\',
                        '/'
                );

        int slash =
                normalized.lastIndexOf('/');

        if (slash >= 0) {

            normalized =
                    normalized.substring(
                            slash + 1
                    );
        }

        int dot =
                normalized.lastIndexOf('.');

        if (dot > 0) {

            normalized =
                    normalized.substring(
                            0,
                            dot
                    );
        }

        return normalized;
    }
}
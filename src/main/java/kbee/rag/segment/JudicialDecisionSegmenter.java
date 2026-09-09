package kbee.rag.segment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import kbee.rag.io.TextFile;
import reactor.core.publisher.Flux;

@Component("judicialDecisionSegmenter")
public class JudicialDecisionSegmenter
        implements Segmenter {

    /*
     * Ejemplos:
     *
     * A la primera cuestión -¿es admisible...?
     * A la segunda cuestión -¿es procedente...?
     * A la tercera cuestión -¿qué resolución...?
     */
    private static final Pattern QUESTION_PATTERN =
            Pattern.compile(
                    "(?im)^\\s*"
                            + "A\\s+la\\s+"
                            + "(primera|segunda|tercera|cuarta|quinta|sexta)"
                            + "\\s+cuesti[oó]n\\b.*$"
            );

    /*
     * Detecta:
     *
     * 1.
     * 1.1.
     * 1.2.
     * 2.
     * 2.1.
     * 3.5.
     */
    private static final Pattern NUMBER_PATTERN =
            Pattern.compile(
                    "(?m)^\\s*"
                            + "(\\d+(?:\\.\\s*\\d+)*)"
                            + "\\.\\s+"
            );

    private static final Pattern VISTOS_PATTERN =
            Pattern.compile(
                    "(?im)^\\s*"
                            + "(VISTO|VISTA|VISTOS|VISTAS)"
                            + "\\s*:"
            );

    private static final Pattern CONSIDERANDO_PATTERN =
            Pattern.compile(
                    "(?im)^\\s*CONSIDERANDO\\s*:"
            );

    /*
     * Detecta:
     *
     * RESUELVE:
     * RESOLVIÓ:
     * RESOLVIO:
     *
     * También intenta capturar:
     *
     * En mérito a los fundamentos...
     * Por ello...
     */
    private static final Pattern RESOLUTION_MARKER_PATTERN =
            Pattern.compile(
                    "(?i)\\b"
                            + "(RESUELVE|RESOLVI[ÓO])"
                            + "\\s*:"
            );

    private static final Pattern MULTIPLE_SPACES =
            Pattern.compile("[ \\t]+");

    private static final Pattern MULTIPLE_NEW_LINES =
            Pattern.compile("\\n{3,}");

    public JudicialDecisionSegmenter() {
    }

    @Override
    public Flux<TextSegment> split(
            TextFile file,
            int segmentSize,
            int overlap) {

        if (file == null) {

            return Flux.error(
                    new IllegalArgumentException(
                            "file must not be null"
                    )
            );
        }

        String documentId =
                file.id();

//        String documentTitle =
//                file.title();
//
//        OffsetDateTime documentDate =
//                file.date();

        String text =
                file.text();

        validate(
                documentId,
                text,
                segmentSize,
                overlap
        );

        if (text == null
                || text.isBlank()) {

            return Flux.empty();
        }

        String normalized =
                normalizeText(
                        text
                );

        /*
         * Primero separamos RESUELVE.
         *
         * Así nunca queda mezclado con
         * la última cuestión o considerando.
         */
        ResolutionSplit resolutionSplit =
                splitResolution(
                        normalized
                );

        List<Section> sections =
                extractSections(
                        resolutionSplit.body()
                );

        if (resolutionSplit.resolution() != null
                && !resolutionSplit.resolution()
                        .isBlank()) {

            sections.add(
                    new Section(
                            "RESUELVE",
                            "RESUELVE",
                            "RESUELVE",
                            resolutionSplit
                                    .resolution()
                                    .trim()
                    )
            );
        }

        if (sections.isEmpty()) {

            sections.add(
                    new Section(
                            "BODY",
                            null,
                            null,
                            normalized
                    )
            );
        }

        List<TextSegment> segments =
                new ArrayList<>();

        int segmentNumber = 0;

        for (Section section : sections) {

            List<String> fragments =
                    splitSectionText(
                            section.text(),
                            segmentSize,
                            overlap
                    );

            int sectionSegmentNumber = 0;

            for (String fragment : fragments) {

                String clean =
                        fragment.trim();

                if (clean.isBlank()) {

                    continue;
                }

                /*
                 * El Segmenter solamente segmenta.
                 *
                 * embeddingText, concepts y propositions
                 * serán completados posteriormente por
                 * SegmentEnhancer.
                 */
                TextSegment segment =
                        new TextSegment(
                                file.id(),
                                file.title(),
                                file.date(),
                                section.id(),
                                section.title(),
                                section.path(),
                                segmentNumber,
                                sectionSegmentNumber,
                                clean,
                                null,
                                file.type(),
                                List.of(),
                                List.of(),
                                file.metadata()
                        );

                segments.add(
                        segment
                );

                segmentNumber++;
                sectionSegmentNumber++;
            }
        }

        return Flux.fromIterable(
                segments
        );
    }

    private void validate(
            String documentId,
            String text,
            int segmentSize,
            int overlap) {

        if (documentId == null
                || documentId.isBlank()) {

            throw new IllegalArgumentException(
                    "documentId must not be blank"
            );
        }

        if (segmentSize <= 0) {

            throw new IllegalArgumentException(
                    "segmentSize must be greater than zero"
            );
        }

        if (overlap < 0) {

            throw new IllegalArgumentException(
                    "overlap must not be negative"
            );
        }

        if (overlap >= segmentSize) {

            throw new IllegalArgumentException(
                    "overlap must be smaller than segmentSize"
            );
        }
    }

    /*
     * =================================================
     * ESTRUCTURA PRINCIPAL
     * =================================================
     */

    private List<Section> extractSections(
            String text) {

        List<Section> result =
                new ArrayList<>();

        /*
         * Primero intentamos formato por cuestiones:
         *
         * A la primera cuestión...
         * A la segunda cuestión...
         */
        List<QuestionBlock> questions =
                splitQuestions(
                        text
                );

        if (!questions.isEmpty()) {

            for (QuestionBlock question : questions) {

                if ("PREAMBLE".equals(
                        question.id())) {

                    result.add(
                            new Section(
                                    "PREAMBLE",
                                    "PREÁMBULO",
                                    "PREÁMBULO",
                                    question.text()
                            )
                    );

                    continue;
                }

                List<Section> numbered =
                        splitNumberedSections(
                                question,
                                question.text()
                        );

                if (numbered.isEmpty()) {

                    result.add(
                            new Section(
                                    question.id(),
                                    question.shortTitle(),
                                    question.shortTitle(),
                                    question.text()
                            )
                    );

                } else {

                    String prefix =
                            textBeforeFirstNumber(
                                    question.text()
                            );

                    if (prefix != null
                            && !prefix.isBlank()) {

                        result.add(
                                new Section(
                                        question.id(),
                                        question.shortTitle(),
                                        question.shortTitle(),
                                        prefix.trim()
                                )
                        );
                    }

                    result.addAll(
                            numbered
                    );
                }
            }

            return result;
        }

        /*
         * Segundo formato:
         *
         * PREAMBLE
         * VISTOS:
         * CONSIDERANDO:
         * 1.
         * 2.
         * ...
         */
        List<Section> vistosConsiderando =
                extractVistosConsiderando(
                        text
                );

        if (!vistosConsiderando.isEmpty()) {

            return vistosConsiderando;
        }

        /*
         * Fallback:
         *
         * Solo numeración.
         */
        List<Section> numbered =
                splitNumberedSections(
                        null,
                        text
                );

        if (!numbered.isEmpty()) {

            /*
             * Preservamos lo anterior
             * al primer número.
             */
            String prefix =
                    textBeforeFirstNumber(
                            text
                    );

            if (prefix != null
                    && !prefix.isBlank()) {

                result.add(
                        new Section(
                                "PREAMBLE",
                                "PREÁMBULO",
                                "PREÁMBULO",
                                prefix.trim()
                        )
                );
            }

            result.addAll(
                    numbered
            );

            return result;
        }

        /*
         * Sin estructura reconocible.
         */
        result.add(
                new Section(
                        "BODY",
                        null,
                        null,
                        text.trim()
                )
        );

        return result;
    }

    /*
     * =================================================
     * VISTOS / CONSIDERANDO
     * =================================================
     */

    private List<Section> extractVistosConsiderando(
            String text) {

        List<Section> result =
                new ArrayList<>();

        Matcher vistosMatcher =
                VISTOS_PATTERN.matcher(
                        text
                );

        Matcher considerandoMatcher =
                CONSIDERANDO_PATTERN.matcher(
                        text
                );

        boolean hasVistos =
                vistosMatcher.find();

        boolean hasConsiderando =
                considerandoMatcher.find();

        /*
         * Caso completo:
         *
         * PREAMBLE
         * VISTOS
         * CONSIDERANDO
         */
        if (hasVistos
                && hasConsiderando
                && vistosMatcher.start()
                        < considerandoMatcher.start()) {

            String preamble =
                    text.substring(
                            0,
                            vistosMatcher.start()
                    ).trim();

            if (!preamble.isBlank()) {

                result.add(
                        new Section(
                                "PREAMBLE",
                                "PREÁMBULO",
                                "PREÁMBULO",
                                preamble
                        )
                );
            }

            String vistos =
                    text.substring(
                            vistosMatcher.end(),
                            considerandoMatcher.start()
                    ).trim();

            if (!vistos.isBlank()) {

                result.add(
                        new Section(
                                "VISTOS",
                                "VISTOS",
                                "VISTOS",
                                vistos
                        )
                );
            }

            String considerando =
                    text.substring(
                            considerandoMatcher.end()
                    ).trim();

            addConsiderandoSections(
                    considerando,
                    result
            );

            return result;
        }

        /*
         * Caso:
         *
         * CONSIDERANDO:
         * 1...
         */
        if (hasConsiderando) {

            String prefix =
                    text.substring(
                            0,
                            considerandoMatcher.start()
                    ).trim();

            if (!prefix.isBlank()) {

                result.add(
                        new Section(
                                "PREAMBLE",
                                "PREÁMBULO",
                                "PREÁMBULO",
                                prefix
                        )
                );
            }

            String considerando =
                    text.substring(
                            considerandoMatcher.end()
                    ).trim();

            addConsiderandoSections(
                    considerando,
                    result
            );

            return result;
        }

        return result;
    }

    private void addConsiderandoSections(
            String considerando,
            List<Section> result) {

        QuestionBlock block =
                new QuestionBlock(
                        "CONSIDERANDO",
                        "CONSIDERANDO",
                        "CONSIDERANDO",
                        considerando
                );

        List<Section> numbered =
                splitNumberedSections(
                        block,
                        considerando
                );

        if (numbered.isEmpty()) {

            result.add(
                    new Section(
                            "CONSIDERANDO",
                            "CONSIDERANDO",
                            "CONSIDERANDO",
                            considerando
                    )
            );

            return;
        }

        String prefix =
                textBeforeFirstNumber(
                        considerando
                );

        if (prefix != null
                && !prefix.isBlank()) {

            result.add(
                    new Section(
                            "CONSIDERANDO",
                            "CONSIDERANDO",
                            "CONSIDERANDO",
                            prefix.trim()
                    )
            );
        }

        result.addAll(
                numbered
        );
    }

    /*
     * =================================================
     * CUESTIONES
     * =================================================
     */

    private List<QuestionBlock> splitQuestions(
            String text) {

        List<QuestionBlock> result =
                new ArrayList<>();

        Matcher matcher =
                QUESTION_PATTERN.matcher(
                        text
                );

        List<QuestionMatch> matches =
                new ArrayList<>();

        while (matcher.find()) {

            String ordinal =
                    matcher.group(
                            1
                    );

            String id =
                    questionId(
                            ordinal
                    );

            String shortTitle =
                    questionTitle(
                            ordinal
                    );

            matches.add(
                    new QuestionMatch(
                            matcher.start(),
                            matcher.end(),
                            id,
                            shortTitle,
                            matcher.group().trim()
                    )
            );
        }

        if (matches.isEmpty()) {

            return result;
        }

        /*
         * Todo lo previo
         * a la primera cuestión.
         */
        int firstStart =
                matches.get(
                        0
                ).start();

        if (firstStart > 0) {

            String preamble =
                    text.substring(
                            0,
                            firstStart
                    ).trim();

            if (!preamble.isBlank()) {

                result.add(
                        new QuestionBlock(
                                "PREAMBLE",
                                "PREÁMBULO",
                                "PREÁMBULO",
                                preamble
                        )
                );
            }
        }

        for (int i = 0;
                i < matches.size();
                i++) {

            QuestionMatch current =
                    matches.get(
                            i
                    );

            int end =
                    i + 1 < matches.size()
                            ? matches.get(
                                    i + 1
                            ).start()
                            : text.length();

            String body =
                    text.substring(
                            current.end(),
                            end
                    ).trim();

            result.add(
                    new QuestionBlock(
                            current.id(),
                            current.shortTitle(),
                            current.originalTitle(),
                            body
                    )
            );
        }

        return result;
    }

    /*
     * =================================================
     * NUMERACIÓN
     * =================================================
     */

    private List<Section> splitNumberedSections(
            QuestionBlock parent,
            String text) {

        List<Section> result =
                new ArrayList<>();

        Matcher matcher =
                NUMBER_PATTERN.matcher(
                        text
                );

        List<NumberMatch> matches =
                new ArrayList<>();

        while (matcher.find()) {

            String sectionId =
                    normalizeSectionId(
                            matcher.group(
                                    1
                            )
                    );

            matches.add(
                    new NumberMatch(
                            matcher.start(),
                            matcher.end(),
                            sectionId
                    )
            );
        }

        if (matches.isEmpty()) {

            return result;
        }

        for (int i = 0;
                i < matches.size();
                i++) {

            NumberMatch current =
                    matches.get(
                            i
                    );

            int end =
                    i + 1 < matches.size()
                            ? matches.get(
                                    i + 1
                            ).start()
                            : text.length();

            String sectionText =
                    text.substring(
                            current.end(),
                            end
                    ).trim();

            if (sectionText.isBlank()) {
                continue;
            }

            String path =
                    current.id();

            if (parent != null
                    && parent.shortTitle() != null
                    && !parent.shortTitle()
                            .isBlank()) {

                path =
                        parent.shortTitle()
                                + " > "
                                + current.id();
            }

            result.add(
                    new Section(
                            current.id(),
                            null,
                            path,
                            sectionText
                    )
            );
        }

        return result;
    }

    private String textBeforeFirstNumber(
            String text) {

        Matcher matcher =
                NUMBER_PATTERN.matcher(
                        text
                );

        if (!matcher.find()) {

            return text;
        }

        if (matcher.start() == 0) {

            return null;
        }

        return text.substring(
                0,
                matcher.start()
        );
    }

    /*
     * =================================================
     * RESOLUCIÓN
     * =================================================
     */

    private ResolutionSplit splitResolution(
            String text) {

        Matcher matcher =
                RESOLUTION_MARKER_PATTERN.matcher(
                        text
                );

        int markerStart =
                -1;

        /*
         * Tomamos la última aparición porque puede
         * mencionarse "resolvió" dentro de antecedentes.
         */
        while (matcher.find()) {

            markerStart =
                    matcher.start();
        }

        if (markerStart < 0) {

            return new ResolutionSplit(
                    text,
                    null
            );
        }

        /*
         * Buscamos el comienzo de la línea donde aparece
         * RESUELVE / RESOLVIÓ.
         */
        int resolutionStart =
                findLineStart(
                        text,
                        markerStart
                );

        /*
         * Revisamos la línea anterior.
         *
         * Si contiene:
         *
         * "Por ello,..."
         * "En mérito a los fundamentos..."
         *
         * también forma parte de la resolución.
         */
        int previousLineStart =
                findPreviousLineStart(
                        text,
                        resolutionStart
                );

        if (previousLineStart >= 0) {

            String previousLine =
                    text.substring(
                            previousLineStart,
                            resolutionStart
                    ).trim();

            if (isResolutionIntroduction(
                    previousLine)) {

                resolutionStart =
                        previousLineStart;
            }
        }

        /*
         * También puede ocurrir todo
         * en una misma línea:
         *
         * Por ello, la Corte ... RESUELVE:
         *
         * En mérito ... la Corte ... RESOLVIO:
         */
        String markerLine =
                text.substring(
                        findLineStart(
                                text,
                                markerStart
                        ),
                        markerStart
                ).trim();

        if (containsResolutionIntroduction(
                markerLine)) {

            resolutionStart =
                    findLineStart(
                            text,
                            markerStart
                    );
        }

        String body =
                text.substring(
                        0,
                        resolutionStart
                ).trim();

        String resolution =
                text.substring(
                        resolutionStart
                ).trim();

        return new ResolutionSplit(
                body,
                resolution
        );
    }

    private int findLineStart(
            String text,
            int position) {

        int index =
                text.lastIndexOf(
                        '\n',
                        Math.max(
                                0,
                                position - 1
                        )
                );

        return index < 0
                ? 0
                : index + 1;
    }

    private int findPreviousLineStart(
            String text,
            int currentLineStart) {

        if (currentLineStart <= 0) {

            return -1;
        }

        int previousLineEnd =
                currentLineStart - 1;

        /*
         * Saltamos líneas vacías.
         */
        while (previousLineEnd > 0
                && Character.isWhitespace(
                        text.charAt(
                                previousLineEnd
                        )
                )) {

            previousLineEnd--;
        }

        int index =
                text.lastIndexOf(
                        '\n',
                        previousLineEnd
                );

        return index < 0
                ? 0
                : index + 1;
    }

    private boolean isResolutionIntroduction(
            String text) {

        if (text == null) {

            return false;
        }

        String value =
                text.trim()
                        .toLowerCase();

        return value.startsWith(
                "por ello"
        )
                || value.startsWith(
                        "en mérito a los fundamentos"
                )
                || value.startsWith(
                        "en merito a los fundamentos"
                );
    }

    private boolean containsResolutionIntroduction(
            String text) {

        if (text == null) {

            return false;
        }

        String value =
                text.trim()
                        .toLowerCase();

        return value.startsWith(
                "por ello"
        )
                || value.startsWith(
                        "en mérito a los fundamentos"
                )
                || value.startsWith(
                        "en merito a los fundamentos"
                );
    }

    /*
     * =================================================
     * CHUNKING
     * =================================================
     */

    private List<String> splitSectionText(
            String text,
            int segmentSize,
            int overlap) {

        List<String> result =
                new ArrayList<>();

        if (text == null
                || text.isBlank()) {

            return result;
        }

        if (text.length()
                <= segmentSize) {

            result.add(
                    text.trim()
            );

            return result;
        }

        String[] paragraphs =
                text.split(
                        "\\n\\s*\\n"
                );

        StringBuilder current =
                new StringBuilder();

        for (String paragraph : paragraphs) {

            String clean =
                    paragraph.trim();

            if (clean.isBlank()) {
                continue;
            }

            /*
             * Párrafo mayor que
             * el chunk máximo.
             */
            if (clean.length()
                    > segmentSize) {

                flushCurrent(
                        current,
                        result
                );

                result.addAll(
                        splitLargeText(
                                clean,
                                segmentSize,
                                overlap
                        )
                );

                continue;
            }

            int projectedLength =
                    current.length()
                            + (
                                    current.length() > 0
                                            ? 2
                                            : 0
                            )
                            + clean.length();

            if (projectedLength
                    <= segmentSize) {

                if (current.length()
                        > 0) {

                    current.append(
                            "\n\n"
                    );
                }

                current.append(
                        clean
                );

                continue;
            }

            String previous =
                    current.toString()
                            .trim();

            if (!previous.isBlank()) {

                result.add(
                        previous
                );
            }

            current.setLength(
                    0
            );

            /*
             * Para fallos probablemente convenga
             * usar overlap = 0.
             *
             * Si se configura overlap > 0,
             * conservamos parte del chunk anterior.
             */
            if (overlap > 0
                    && !previous.isBlank()) {

                String tail =
                        tail(
                                previous,
                                overlap
                        );

                if (!tail.isBlank()) {

                    current.append(
                            tail
                    )
                    .append(
                            "\n\n"
                    );
                }
            }

            current.append(
                    clean
            );
        }

        flushCurrent(
                current,
                result
        );

        return result;
    }

    private List<String> splitLargeText(
            String text,
            int segmentSize,
            int overlap) {

        List<String> result =
                new ArrayList<>();

        int start =
                0;

        while (start
                < text.length()) {

            int desiredEnd =
                    Math.min(
                            start + segmentSize,
                            text.length()
                    );

            int end =
                    findNaturalBreak(
                            text,
                            start,
                            desiredEnd
                    );

            if (end <= start) {

                end =
                        desiredEnd;
            }

            String fragment =
                    text.substring(
                            start,
                            end
                    ).trim();

            if (!fragment.isBlank()) {

                result.add(
                        fragment
                );
            }

            if (end
                    >= text.length()) {

                break;
            }

            int nextStart =
                    Math.max(
                            start + 1,
                            end - overlap
                    );

            if (overlap == 0) {

                nextStart =
                        end;

            } else {

                while (nextStart < end
                        && nextStart > 0
                        && !Character.isWhitespace(
                                text.charAt(
                                        nextStart
                                )
                        )) {

                    nextStart++;
                }
            }

            start =
                    Math.min(
                            nextStart,
                            text.length()
                    );
        }

        return result;
    }

    private int findNaturalBreak(
            String text,
            int start,
            int desiredEnd) {

        if (desiredEnd
                >= text.length()) {

            return text.length();
        }

        int minimum =
                start
                        + (int) (
                                (desiredEnd - start)
                                        * 0.65
                        );

        /*
         * Intentamos terminar en oración.
         */
        for (int i = desiredEnd;
                i >= minimum;
                i--) {

            char c =
                    text.charAt(
                            i - 1
                    );

            if (c == '.'
                    || c == ';'
                    || c == ':'
                    || c == '?'
                    || c == '!') {

                if (i >= text.length()
                        || Character.isWhitespace(
                                text.charAt(
                                        i
                                )
                        )) {

                    return i;
                }
            }
        }

        /*
         * Si no, terminamos en espacio.
         */
        for (int i = desiredEnd;
                i >= minimum;
                i--) {

            if (Character.isWhitespace(
                    text.charAt(
                            i - 1
                    )
            )) {

                return i;
            }
        }

        return desiredEnd;
    }

    private void flushCurrent(
            StringBuilder current,
            List<String> result) {

        if (current.length() == 0) {

            return;
        }

        String value =
                current.toString()
                        .trim();

        if (!value.isBlank()) {

            result.add(
                    value
            );
        }

        current.setLength(
                0
        );
    }

    private String tail(
            String value,
            int maxLength) {

        if (value == null
                || value.isBlank()
                || maxLength <= 0) {

            return "";
        }

        if (value.length()
                <= maxLength) {

            return value;
        }

        int start =
                value.length()
                        - maxLength;

        while (start
                < value.length()
                && !Character.isWhitespace(
                        value.charAt(
                                start
                        )
                )) {

            start++;
        }

        if (start
                >= value.length()) {

            return "";
        }

        return value.substring(
                start
        ).trim();
    }

    /*
     * =================================================
     * NORMALIZACIÓN
     * =================================================
     */

    private String normalizeText(
            String text) {

        String normalized =
                text.replace(
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

        normalized =
                MULTIPLE_SPACES
                        .matcher(
                                normalized
                        )
                        .replaceAll(
                                " "
                        );

        normalized =
                MULTIPLE_NEW_LINES
                        .matcher(
                                normalized
                        )
                        .replaceAll(
                                "\n\n"
                        );

        return normalized.trim();
    }

    private String normalizeSectionId(
            String value) {

        if (value == null) {

            return null;
        }

        return value
                .replaceAll(
                        "\\s+",
                        ""
                )
                .replaceAll(
                        "\\.$",
                        ""
                )
                .trim();
    }

    /*
     * =================================================
     * HELPERS DE CUESTIONES
     * =================================================
     */

    private String questionId(
            String ordinal) {

        if (ordinal == null) {

            return "QUESTION";
        }

        return switch (
                ordinal.toLowerCase()
        ) {

            case "primera" ->
                    "QUESTION-1";

            case "segunda" ->
                    "QUESTION-2";

            case "tercera" ->
                    "QUESTION-3";

            case "cuarta" ->
                    "QUESTION-4";

            case "quinta" ->
                    "QUESTION-5";

            case "sexta" ->
                    "QUESTION-6";

            default ->
                    "QUESTION";
        };
    }

    private String questionTitle(
            String ordinal) {

        if (ordinal == null) {

            return "CUESTIÓN";
        }

        return switch (
                ordinal.toLowerCase()
        ) {

            case "primera" ->
                    "PRIMERA CUESTIÓN";

            case "segunda" ->
                    "SEGUNDA CUESTIÓN";

            case "tercera" ->
                    "TERCERA CUESTIÓN";

            case "cuarta" ->
                    "CUARTA CUESTIÓN";

            case "quinta" ->
                    "QUINTA CUESTIÓN";

            case "sexta" ->
                    "SEXTA CUESTIÓN";

            default ->
                    "CUESTIÓN";
        };
    }

    /*
     * =================================================
     * RECORDS INTERNOS
     * =================================================
     */

    private record Section(
            String id,
            String title,
            String path,
            String text) {
    }

    private record QuestionBlock(
            String id,
            String shortTitle,
            String originalTitle,
            String text) {
    }

    private record QuestionMatch(
            int start,
            int end,
            String id,
            String shortTitle,
            String originalTitle) {
    }

    private record NumberMatch(
            int start,
            int end,
            String id) {
    }

    private record ResolutionSplit(
            String body,
            String resolution) {
    }
}
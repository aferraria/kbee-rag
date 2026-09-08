package kbee.rag.segment;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HeadingDetector {

    /*
     * 1. General
     * 1.1 General
     * 1.2.3 Inspection Requirements
     */
    private static final Pattern NUMERIC_HEADING =
            Pattern.compile(
                    "^\\s*(\\d+(?:\\.\\d+)*)\\.?\\s+(.+?)\\s*$"
            );

    /*
     * A. Physical Description
     * B. Dust Cover Assembly
     */
    private static final Pattern LETTER_HEADING =
            Pattern.compile(
                    "^\\s*([A-Z])\\.\\s+(.+?)\\s*$"
            );

    /*
     * (1) Clean mounting surface...
     * (2) Apply primer...
     * (a) Remove screw...
     *
     * Esto NO necesariamente crea sección.
     * Es útil para detectar items/procedimientos.
     */
    private static final Pattern ITEM =
            Pattern.compile(
                    "^\\s*\\(([0-9]+|[a-zA-Z])\\)\\s+(.+?)\\s*$"
            );

    /*
     * DESCRIPTION AND OPERATION
     * TESTING AND TROUBLESHOOTING
     * ASSEMBLY
     * CLEANING
     *
     * Título completo en mayúsculas.
     */
    private static final Pattern UPPERCASE_HEADING =
            Pattern.compile(
                    "^\\s*([A-Z][A-Z0-9 /,&()\\-]{3,})\\s*$"
            );

    public Heading detect(String line) {

        if (line == null || line.isBlank()) {
            return null;
        }

        String normalized =
                line.trim();

        /*
         * -------------------------------------------------
         * NUMÉRICO
         * -------------------------------------------------
         */
        Matcher numeric =
                NUMERIC_HEADING.matcher(normalized);

        if (numeric.matches()) {

            String number =
                    numeric.group(1);

            String title =
                    numeric.group(2).trim();

            int depth =
                    number.split("\\.").length;

            /*
             * Nivel:
             *
             * 1       -> 2
             * 1.1     -> 3
             * 1.1.1   -> 4
             *
             * Dejamos nivel 1 para headings mayores
             * tipo DESCRIPTION AND OPERATION.
             */
            int level =
                    depth + 1;

            return new Heading(
                    HeadingType.NUMERIC,
                    level,
                    number,
                    normalized,
                    title
            );
        }

        /*
         * -------------------------------------------------
         * LETRA
         * -------------------------------------------------
         */
        Matcher letter =
                LETTER_HEADING.matcher(normalized);

        if (letter.matches()) {

            String code =
                    letter.group(1);

            String title =
                    letter.group(2).trim();

            /*
             * A., B., C. generalmente son un nivel
             * inferior a 1., 2., etc.
             */
            return new Heading(
                    HeadingType.LETTER,
                    3,
                    code,
                    normalized,
                    title
            );
        }

        /*
         * -------------------------------------------------
         * ITEM
         * -------------------------------------------------
         */
        Matcher item =
                ITEM.matcher(normalized);

        if (item.matches()) {

            return new Heading(
                    HeadingType.ITEM,
                    4,
                    item.group(1),
                    normalized,
                    item.group(2).trim()
            );
        }

        /*
         * -------------------------------------------------
         * HEADING MAYÚSCULAS
         * -------------------------------------------------
         */
        Matcher uppercase =
                UPPERCASE_HEADING.matcher(normalized);

        if (uppercase.matches()
                && looksLikeHeading(normalized)) {

            return new Heading(
                    HeadingType.MAJOR,
                    1,
                    normalizeId(normalized),
                    normalized,
                    normalized
            );
        }

        return null;
    }

    /*
     * Evita considerar cualquier línea en mayúsculas
     * como sección.
     */
    private boolean looksLikeHeading(
            String value) {

        int words =
                value.split("\\s+").length;

        if (words > 12) {
            return false;
        }

        /*
         * Una oración que termina con punto suele ser
         * contenido, no heading.
         */
        if (value.endsWith(".")) {
            return false;
        }

        /*
         * Evitar líneas excesivamente largas.
         */
        if (value.length() > 100) {
            return false;
        }

        return true;
    }

    private String normalizeId(
            String value) {

        return value
                .toLowerCase(Locale.ROOT)
                .replaceAll(
                        "[^a-z0-9]+",
                        "-"
                )
                .replaceAll(
                        "^-+|-+$",
                        ""
                );
    }

    public enum HeadingType {
        MAJOR,
        NUMERIC,
        LETTER,
        ITEM
    }

    public record Heading(
            HeadingType type,
            int level,
            String code,
            String rawTitle,
            String title) {
    }
}
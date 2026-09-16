package kbee.rag.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class JudicialFilterBuilder
        implements FilterQueryBuilder {

    private static final String FROM_DATE =
            "fromDate";

    private static final String TO_DATE =
            "toDate";

    @Override
    public List<String> build(
            Map<String, String> parameters) {

        List<String> filters =
                new ArrayList<>();

        if (parameters == null
                || parameters.isEmpty()) {

            return filters;
        }

        String fromDate =
                normalizeDate(
                        getValue(
                                parameters,
                                FROM_DATE
                        ));

        String toDate =
                normalizeDate(
                        getValue(
                                parameters,
                                TO_DATE
                        ));

        if (fromDate != null
                || toDate != null) {

            filters.add(
                    "document_date:["
                            + (fromDate == null
                                    ? "*"
                                    : fromDate)
                            + " TO "
                            + (toDate == null
                                    ? "*"
                                    : toDate)
                            + "]"
            );
        }

        return filters;
    }

    private String getValue(
            Map<String, String> parameters,
            String name) {

        Object value =
                parameters.get(name);

        if (value == null) {
            return null;
        }

        String text =
                value.toString().trim();

        return text.isBlank()
                ? null
                : text;
    }

    /**
     * Normalizes an ISO-8601 date/date-time string to the strict UTC instant
     * format required by Solr (e.g. {@code 2025-09-16T03:00:00Z}). Accepts
     * instants ({@code ...Z}), offset date-times ({@code 2025-09-16T00:00-03:00}),
     * local date-times and plain dates ({@code 2025-09-16}). Returns the input
     * unchanged if it cannot be parsed.
     */
    private String normalizeDate(String text) {

        if (text == null) {
            return null;
        }

        try {
            return java.time.Instant.parse(text).toString();
        } catch (java.time.format.DateTimeParseException ignore) {
        }

        try {
            return java.time.OffsetDateTime.parse(text).toInstant().toString();
        } catch (java.time.format.DateTimeParseException ignore) {
        }

        try {
            return java.time.LocalDateTime.parse(text)
                    .atOffset(java.time.ZoneOffset.UTC).toInstant().toString();
        } catch (java.time.format.DateTimeParseException ignore) {
        }

        try {
            return java.time.LocalDate.parse(text)
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toString();
        } catch (java.time.format.DateTimeParseException ignore) {
        }

        return text;
    }
}
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
                getValue(
                        parameters,
                        FROM_DATE
                );

        String toDate =
                getValue(
                        parameters,
                        TO_DATE
                );

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
}
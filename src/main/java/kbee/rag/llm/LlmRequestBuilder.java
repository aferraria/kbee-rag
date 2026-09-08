package kbee.rag.llm;

import java.util.List;

import org.springframework.stereotype.Component;

import kbee.rag.search.ExpandedSource;
import kbee.rag.search.SegmentSearchResult;

@Component
public class LlmRequestBuilder {

    public LlmRequest build(
            String question,
            List<ExpandedSource> sources) {

        return new LlmRequest(
                buildInstructions(),
                buildInput(
                        question,
                        sources
                ),
                null
        );
    }

    private String buildInstructions() {

        return """
                Responde la consulta utilizando exclusivamente
                la información contenida en las fuentes proporcionadas.

                Reglas:

                1. No utilices conocimiento externo.
                2. No inventes hechos, normas ni conclusiones.
                3. Si las fuentes no permiten responder con certeza,
                   indícalo expresamente.
                4. Cita las fuentes utilizando el formato [Fuente N].
                5. Prioriza las fuentes que respondan directamente
                   a la consulta.
                6. Evita información irrelevante.
                """;
    }

    private String buildInput(
            String question,
            List<ExpandedSource> sources) {

        StringBuilder input =
                new StringBuilder();

        input.append(
                "PREGUNTA DEL USUARIO\n\n"
        );

        input.append(
                question
        );

        input.append(
                "\n\nFUENTES\n\n"
        );

        int sourceNumber = 1;

        for (ExpandedSource source :
                sources) {

            appendSource(
                    input,
                    sourceNumber,
                    source
            );

            sourceNumber++;
        }

        return input.toString();
    }

    private void appendSource(
            StringBuilder input,
            int sourceNumber,
            ExpandedSource source) {

        SegmentSearchResult selected =
                source.selected();

        input.append(
                "===== FUENTE "
                        + sourceNumber
                        + " =====\n"
        );

        if (selected.documentTitle() != null
                && !selected.documentTitle().isBlank()) {

            input.append(
                    "Título: "
                            + selected.documentTitle()
                            + "\n"
            );
        }

        if (selected.documentDate() != null) {

            input.append(
                    "Fecha: "
                            + selected.documentDate()
                            + "\n"
            );
        }

        if (selected.sectionPath() != null
                && !selected.sectionPath().isBlank()) {

            input.append(
                    "Sección: "
                            + selected.sectionPath()
                            + "\n"
            );
        }

        input.append("\n");

        if (selected.text() != null
                && !selected.text().isBlank()) {

            input.append(
                    selected.text()
            );

            input.append(
                    "\n\n"
            );
        }

        for (SegmentSearchResult segment :
                source.contextSegments()) {

            if (segment.text() != null
                    && !segment.text().isBlank()) {

                input.append(
                        segment.text()
                );

                input.append(
                        "\n\n"
                );
            }
        }
    }
}
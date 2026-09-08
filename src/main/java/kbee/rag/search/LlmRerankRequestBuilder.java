package kbee.rag.search;

import java.util.List;

import org.springframework.stereotype.Component;

import kbee.rag.reranker.RerankRequest;

@Component
public class LlmRerankRequestBuilder {

    public RerankRequest build(
            String query,
            List<ExpandedSource> sources) {

        return new RerankRequest(
                buildInstructions(),
                query,
                sources
        );
    }

    private String buildInstructions() {

        return """
                Eres un reranker especializado en jurisprudencia.

                Tu única tarea es ordenar los documentos candidatos
                según su relevancia jurídica respecto de la consulta.

                No respondas la consulta.
                No resumas los documentos.
                No utilices conocimiento externo.

                Evalúa especialmente:

                1. Si el documento responde directamente
                   a la cuestión jurídica planteada.

                2. La correspondencia entre los hechos
                   jurídicamente relevantes del documento
                   y los planteados en la consulta.

                3. La coincidencia de normas, instituciones
                   y conceptos jurídicos relevantes.

                4. Si el tribunal interpreta, aplica o establece
                   un criterio sobre la cuestión consultada,
                   en lugar de realizar una mera mención incidental.

                5. La utilidad del documento como precedente
                   para resolver la cuestión planteada.

                Las coincidencias meramente textuales deben tener
                importancia secundaria.

                Asigna a cada documento un score entre 0 y 1,
                donde 1 representa máxima relevancia jurídica.

                Devuelve exclusivamente JSON con esta estructura:

                {
                  "ranking": [
                    {
                      "index": 0,
                      "score": 0.95
                    }
                  ]
                }

                Incluye todos los documentos evaluados.
                Ordena el ranking de mayor a menor score.
                """;
    }
}
package kbee.rag.thesaurus;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.springframework.stereotype.Component;

@Component
public class SpanishEmbeddingTextNormalizer
        implements EmbeddingTextNormalizer {

    @Override
    public String normalize(
            String text) {

        if (text == null
                || text.isBlank()) {

            return "";
        }

        try (Analyzer analyzer =
                new SpanishAnalyzer()) {

            try (TokenStream tokenStream =
                    analyzer.tokenStream(
                            "text",
                            text
                    )) {

                CharTermAttribute term =
                        tokenStream.addAttribute(
                                CharTermAttribute.class
                        );

                StringBuilder result =
                        new StringBuilder();

                tokenStream.reset();

                while (tokenStream.incrementToken()) {

                    if (!result.isEmpty()) {
                        result.append(' ');
                    }

                    result.append(
                            term.toString()
                    );
                }

                tokenStream.end();

                return result.toString();
            }

        } catch (IOException e) {

            throw new IllegalStateException(
                    "No fue posible normalizar texto "
                            + "para embedding",
                    e
            );
        }
    }
}
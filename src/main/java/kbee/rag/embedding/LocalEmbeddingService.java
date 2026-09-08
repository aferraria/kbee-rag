package kbee.rag.embedding;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

@Service
@Configuration
@ConditionalOnProperty(
        prefix = "embedding",
        name = "provider",
        havingValue = "local",
        matchIfMissing = true
)
public class LocalEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public LocalEmbeddingService(
            EmbeddingModel embeddingModel) {

        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<List<Float>> embed(
            List<String> texts) {

        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<TextSegment> segments = texts.stream()
                .map(TextSegment::from)
                .toList();

        List<Embedding> embeddings =
                embeddingModel.embedAll(segments)
                        .content();

        if (embeddings.size() != texts.size()) {
            throw new IllegalStateException(
                    "Cantidad incorrecta de embeddings. Esperados="
                    + texts.size()
                    + ", recibidos="
                    + embeddings.size()
            );
        }

        return embeddings.stream()
                .map(Embedding::vectorAsList)
                .toList();
    }
}
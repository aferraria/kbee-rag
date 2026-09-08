package kbee.rag;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import kbee.rag.embedding.EmbeddingService;

@Component
@ConditionalOnProperty(
        prefix = "embedding-test",
        name = "enabled",
        havingValue = "true"
)
public class EmbeddingTestRunner
        implements ApplicationRunner {

    private final EmbeddingService embeddingService;

    public EmbeddingTestRunner(
            EmbeddingService embeddingService) {

        System.out.println(
                "Creando EmbeddingTestRunner"
        );
        this.embeddingService =
                embeddingService;
    }

    @Override
    public void run(
            ApplicationArguments args) {

        List<String> texts = List.of(
                "Configuración de autenticación LDAP",
                "LDAP authentication configuration",
                "Receta para preparar una pizza"
        );

        List<List<Float>> result =
                embeddingService.embed(texts);

        for (int index = 0;
                index < result.size();
                index++) {

            System.out.printf(
                    "Embedding %d: dimensión=%d%n",
                    index,
                    result.get(index).size()
            );
        }
    }
}
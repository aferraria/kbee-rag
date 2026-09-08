package kbee.rag.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class ResourceInstructionProvider
        implements InstructionProvider {

    private final ResourceLoader resourceLoader;

    public ResourceInstructionProvider(
            ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public String get(String name) {

        String path =
                "classpath:prompts/" + name + ".txt";

        try {
            Resource resource =
                    resourceLoader.getResource(path);

            return resource.getContentAsString(
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo cargar el prompt: " + path,
                    e
            );
        }
    }
}
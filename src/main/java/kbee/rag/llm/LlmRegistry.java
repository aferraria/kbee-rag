package kbee.rag.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Registry of all the {@link LlmService} beans available
 * in the application context, keyed by provider id.
 *
 * The default provider is configured with {@code llm.provider}
 * and is used whenever the client does not specify one.
 */
@Component
public class LlmRegistry {

    private final Map<String, LlmService> services;

    private final String defaultProvider;

    public LlmRegistry(
            List<LlmService> llmServices,
            @Value("${llm.provider}")
            String defaultProvider) {

        this.services =
                new LinkedHashMap<>();

        for (LlmService service : llmServices) {
            this.services.put(
                    service.providerId(),
                    service
            );
        }

        this.defaultProvider =
                defaultProvider;

        if (!this.services.containsKey(defaultProvider)) {
            throw new IllegalStateException(
                    "Default LLM provider not available: "
                            + defaultProvider
                            + ". Available: "
                            + this.services.keySet()
            );
        }

        System.out.println(
                "===== LLM PROVIDERS: "
                        + this.services.keySet()
                        + " (default: "
                        + defaultProvider
                        + ") ====="
        );
    }

    /**
     * Resolves the provider id to use: the requested one
     * if present, otherwise the configured default.
     */
    public String resolveProviderId(String provider) {

        if (provider == null || provider.isBlank()) {
            return defaultProvider;
        }
        return provider.trim().toLowerCase();
    }

    /**
     * Returns the service for the given provider,
     * or the default service when provider is null/blank.
     *
     * @throws LlmException if the provider is unknown
     */
    public LlmService get(String provider) {

        String id =
                resolveProviderId(provider);

        LlmService service =
                services.get(id);

        if (service == null) {
            throw new LlmException(
                    "Unknown LLM provider: "
                            + id
                            + ". Available: "
                            + services.keySet()
            );
        }
        return service;
    }

    public LlmService getDefault() {
        return get(null);
    }

    public Set<String> availableProviders() {
        return services.keySet();
    }
}

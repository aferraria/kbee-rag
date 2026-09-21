package kbee.rag;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.solr.client.solrj.SolrClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import kbee.rag.audit.Logger;
import kbee.rag.audit.ServerConstant;

/**
 * On startup, checks which external services configured in application.yml
 * are operational and logs the result.
 */
@Component
public class ExternalServicesStartupCheck implements ApplicationRunner {

    static private Logger startupLogger = Logger.getLogger("StartupLogger");

    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(5);

    private final SolrClient sourceSolrClient;
    private final SolrClient targetSolrClient;

    private final String solrSourceBaseUrl;
    private final String solrSourceCore;
    private final String solrTargetBaseUrl;
    private final String solrTargetCore;

    private final String embeddingProvider;
    private final String embeddingQwenBaseUrl;
    private final String embeddingMultilingualBaseUrl;

    private final boolean ollamaEnabled;
    private final String ollamaBaseUrl;
    private final boolean openaiEnabled;
    private final String openaiBaseUrl;
    private final String openaiApiKey;
    private final boolean claudeEnabled;
    private final String claudeBaseUrl;
    private final boolean openrouterEnabled;
    private final String openrouterBaseUrl;

    private final boolean rerankerEnabled;
    private final String rerankerProvider;
    private final String rerankerLocalBaseUrl;
    private final String rerankerOpenrouterBaseUrl;

    public ExternalServicesStartupCheck(
            @Qualifier("sourceSolrClient") SolrClient sourceSolrClient,
            @Qualifier("targetSolrClient") SolrClient targetSolrClient,
            @Value("${solr.source.base-url}") String solrSourceBaseUrl,
            @Value("${solr.source.core}") String solrSourceCore,
            @Value("${solr.target.base-url}") String solrTargetBaseUrl,
            @Value("${solr.target.core}") String solrTargetCore,
            @Value("${embedding.provider:}") String embeddingProvider,
            @Value("${embedding.qwen-local.base-url:}") String embeddingQwenBaseUrl,
            @Value("${embedding.local-multilingual.base-url:}") String embeddingMultilingualBaseUrl,
            @Value("${llm.ollama.enabled:false}") boolean ollamaEnabled,
            @Value("${llm.ollama.base-url:}") String ollamaBaseUrl,
            @Value("${llm.openai.enabled:false}") boolean openaiEnabled,
            @Value("${llm.openai.base-url:}") String openaiBaseUrl,
            @Value("${llm.openai.api-key:}") String openaiApiKey,
            @Value("${llm.claude.enabled:false}") boolean claudeEnabled,
            @Value("${llm.claude.base-url:}") String claudeBaseUrl,
            @Value("${llm.openrouter.enabled:false}") boolean openrouterEnabled,
            @Value("${llm.openrouter.base-url:}") String openrouterBaseUrl,
            @Value("${reranker.enabled:false}") boolean rerankerEnabled,
            @Value("${reranker.provider:}") String rerankerProvider,
            @Value("${reranker.local.base-url:}") String rerankerLocalBaseUrl,
            @Value("${reranker.openrouter.base-url:}") String rerankerOpenrouterBaseUrl) {

        this.sourceSolrClient = sourceSolrClient;
        this.targetSolrClient = targetSolrClient;
        this.solrSourceBaseUrl = solrSourceBaseUrl;
        this.solrSourceCore = solrSourceCore;
        this.solrTargetBaseUrl = solrTargetBaseUrl;
        this.solrTargetCore = solrTargetCore;
        this.embeddingProvider = embeddingProvider;
        this.embeddingQwenBaseUrl = embeddingQwenBaseUrl;
        this.embeddingMultilingualBaseUrl = embeddingMultilingualBaseUrl;
        this.ollamaEnabled = ollamaEnabled;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.openaiEnabled = openaiEnabled;
        this.openaiBaseUrl = openaiBaseUrl;
        this.openaiApiKey = openaiApiKey;
        this.claudeEnabled = claudeEnabled;
        this.claudeBaseUrl = claudeBaseUrl;
        this.openrouterEnabled = openrouterEnabled;
        this.openrouterBaseUrl = openrouterBaseUrl;
        this.rerankerEnabled = rerankerEnabled;
        this.rerankerProvider = rerankerProvider;
        this.rerankerLocalBaseUrl = rerankerLocalBaseUrl;
        this.rerankerOpenrouterBaseUrl = rerankerOpenrouterBaseUrl;
    }

    @Override
    public void run(ApplicationArguments args) {

        List<String> report = new ArrayList<>();

        // Solr
        report.add(status("Solr source  (" + solrSourceBaseUrl + " / core: " + solrSourceCore + ")",
                checkSolr(sourceSolrClient, solrSourceCore)));
      
        report.add(status("Solr target  (" + solrTargetBaseUrl + " / core: " + solrTargetCore + ")",
                checkSolr(targetSolrClient, solrTargetCore)));

        
        // Embedding provider
        if ("qwen-local".equals(embeddingProvider) && StringUtils.hasText(embeddingQwenBaseUrl))
            report.add(status("Embedding qwen-local (" + embeddingQwenBaseUrl + ")", checkHttp(embeddingQwenBaseUrl)));
        else if ("local-multilingual".equals(embeddingProvider) && StringUtils.hasText(embeddingMultilingualBaseUrl))
            report.add(status("Embedding local-multilingual (" + embeddingMultilingualBaseUrl + ")",
                    checkHttp(embeddingMultilingualBaseUrl)));
        else
            report.add("SKIP -> Embedding provider '" + embeddingProvider + "' (no remote endpoint to check)");

        // LLM providers
        if (ollamaEnabled)
            report.add(status("LLM ollama (" + ollamaBaseUrl + ")", checkHttp(ollamaBaseUrl)));
        if (openaiEnabled)
            report.add(status("LLM openai (" + openaiBaseUrl + ")",
                    StringUtils.hasText(openaiApiKey) && checkHttp(openaiBaseUrl + "/models", openaiApiKey)));
        if (claudeEnabled)
            report.add(status("LLM claude (" + claudeBaseUrl + ")", checkHttp(claudeBaseUrl)));
        if (openrouterEnabled)
            report.add(status("LLM openrouter (" + openrouterBaseUrl + ")", checkHttp(openrouterBaseUrl + "/models")));

        
        
        // Reranker
        if (rerankerEnabled) {
            String rerankerUrl = "openrouter".equals(rerankerProvider)
                    ? rerankerOpenrouterBaseUrl
                    : rerankerLocalBaseUrl;
            report.add(status("Reranker " + rerankerProvider + " (" + rerankerUrl + ")",
                    checkHttp(rerankerUrl + "/models")));
        }

        startupLogger.info(ServerConstant.SEPARATOR);
        startupLogger.info("External services status:");
        startupLogger.info("");
        report.forEach(startupLogger::info);
        startupLogger.info(ServerConstant.SEPARATOR);
    }

    private String status(String name, boolean ok) {
        return (ok ? "UP   -> " : "DOWN -> ") + name;
    }

    private boolean checkSolr(SolrClient client, String core) {
        try {
            client.ping(core);
            return true;
        } catch (Exception e) {
            startupLogger.debug("Solr check failed for core '" + core + "': " + e.getMessage());
            return false;
        }
    }

    private boolean checkHttp(String url) {
        return checkHttp(url, null);
    }

    /**
     * Considers the service operational if it answers any HTTP status < 500
     * (4xx means reachable but e.g. auth required / not found path).
     */
    private boolean checkHttp(String url, String bearerToken) {
        if (!StringUtils.hasText(url))
            return false;
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(CHECK_TIMEOUT).build()) {
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url))
                    .timeout(CHECK_TIMEOUT)
                    .GET();
            if (StringUtils.hasText(bearerToken))
                rb.header("Authorization", "Bearer " + bearerToken);
            HttpResponse<Void> response = client.send(rb.build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 500;
        } catch (Exception e) {
            startupLogger.debug("HTTP check failed for '" + url + "': " + e.getMessage());
            return false;
        }
    }
}

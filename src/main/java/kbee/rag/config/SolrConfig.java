package kbee.rag.config;

import java.util.concurrent.TimeUnit;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SolrConfig {

    @Bean(
        name = "sourceSolrClient",
        destroyMethod = "close"
    )
    public SolrClient sourceSolrClient(
            @Value("${solr.source.base-url}")
            String baseUrl,
            @Value("${solr.source.connection-timeout-ms:5000}")
            long connectionTimeoutMs,
            @Value("${solr.source.request-timeout-ms:30000}")
            long requestTimeoutMs) {

        return new HttpJdkSolrClient.Builder(baseUrl)
                .useHttp1_1(true)
                .withConnectionTimeout(
                        connectionTimeoutMs,
                        TimeUnit.MILLISECONDS
                )
                .withRequestTimeout(
                        requestTimeoutMs,
                        TimeUnit.MILLISECONDS
                )
                .build();
    }

    @Primary
    @Bean(
        name = "targetSolrClient",
        destroyMethod = "close"
    )
    public SolrClient targetSolrClient(
            @Value("${solr.target.base-url}")
            String baseUrl,
            @Value("${solr.target.connection-timeout-ms:5000}")
            long connectionTimeoutMs,
            @Value("${solr.target.request-timeout-ms:30000}")
            long requestTimeoutMs) {

        return new HttpJdkSolrClient.Builder(baseUrl)
                .useHttp1_1(true)
                .withConnectionTimeout(
                        connectionTimeoutMs,
                        TimeUnit.MILLISECONDS
                )
                .withRequestTimeout(
                        requestTimeoutMs,
                        TimeUnit.MILLISECONDS
                )
                .build();
    }
}
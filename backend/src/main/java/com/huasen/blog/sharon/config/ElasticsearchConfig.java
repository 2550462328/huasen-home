package com.huasen.blog.sharon.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.Data;

/**
 * Elasticsearch条件化配置
 * 仅在huasen.elasticsearch.enabled=true时生效
 * 按D-11: 配置文件开关控制ES启用
 */
@Configuration
@ConditionalOnProperty(prefix = "huasen.elasticsearch", name = "enabled", havingValue = "true")
public class ElasticsearchConfig {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchConfig.class);

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchProperties properties) {
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        if (properties.getUsername() != null && !properties.getUsername().isEmpty()) {
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(properties.getUsername(), properties.getPassword())
            );
        }

        RestClient restClient = RestClient.builder(
                new HttpHost(properties.getHost(), properties.getPort(), "http")
        ).setHttpClientConfigCallback(httpClientBuilder ->
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
        ).build();

        RestClientTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper()
        );

        ElasticsearchClient client = new ElasticsearchClient(transport);

        // Connection test at startup
        try {
            boolean pingResult = client.ping().value();
            if (pingResult) {
                logger.info("Elasticsearch connection successful: {}:{}", properties.getHost(), properties.getPort());
            } else {
                logger.warn("Elasticsearch ping returned false: {}:{}", properties.getHost(), properties.getPort());
            }
        } catch (Exception e) {
            logger.warn("Elasticsearch connection test failed: {}:{} - {}",
                    properties.getHost(), properties.getPort(), e.getMessage());
        }

        return client;
    }

    @Component
    @ConfigurationProperties(prefix = "huasen.elasticsearch")
    @ConditionalOnProperty(prefix = "huasen.elasticsearch", name = "enabled", havingValue = "true")
    @Data
    public static class ElasticsearchProperties {
        private boolean enabled = false;
        private String host = "127.0.0.1";
        private int port = 9200;
        private String username = "elastic";
        private String password = "";

        @PostConstruct
        public void init() {
            logger.info("Elasticsearch configuration loaded: {}:{}", host, port);
        }

        private static final Logger logger = LoggerFactory.getLogger(ElasticsearchProperties.class);
    }
}

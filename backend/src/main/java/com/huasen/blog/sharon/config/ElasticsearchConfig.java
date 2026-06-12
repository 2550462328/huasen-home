package com.huasen.blog.sharon.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
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
        ).setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                .setDefaultCredentialsProvider(credentialsProvider)
                // 兼容 ES 7.5.0 服务端:co.elastic.clients(7.15+)在每个请求上强制发送
                // 厂商专用媒体类型 "application/vnd.elasticsearch+json; compatible-with=7"。
                // 该 REST API 兼容媒体类型自 ES 7.11 才引入,7.5.0 不认识,返回
                // 406 "Content-Type header [...] is not supported"。这里在请求链把
                // Content-Type / Accept 改回普通 application/json,7.5.0 可原生识别。
                .addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
                    request.setHeader("Content-Type", "application/json");
                    request.setHeader("Accept", "application/json");
                })
                // 兼容 ES 7.5.0 服务端:7.14 之前的 ES 不会返回 X-Elastic-Product 响应头,
                // 而 7.15+ 客户端(co.elastic.clients)在 RestClientTransport 里强制校验该头,
                // 缺失就抛 "Missing [X-Elastic-Product] header"。这里在响应链补一个伪造 header,
                // 让客户端校验通过。仅在已确认对端确实是 Elasticsearch 时使用。
                .addInterceptorLast((HttpResponseInterceptor) (response, context) -> {
                    if (!response.containsHeader("X-Elastic-Product")) {
                        response.setHeader("X-Elastic-Product", "Elasticsearch");
                    }
                })
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

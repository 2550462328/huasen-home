package com.huasen.common.config;

import com.alibaba.dashscope.aigc.generation.Generation;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * 阿里百炼大模型配置
 * 提供 Generation 客户端 bean 和相关配置属性
 */
@Configuration
public class BailianConfig {

    private static final Logger logger = LoggerFactory.getLogger(BailianConfig.class);

    @Bean
    public Generation bailianGeneration(BailianProperties properties) {
        // Instantiate the thread-safe Generation client
        // Per DashScope SDK documentation: "One instance is reusable and thread-safe"
        Generation generation = new Generation();

        // Startup validation: warn if API key is blank
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            logger.warn("Bailian API key not configured — AI features will fail at runtime");
        } else {
            logger.info("Bailian Generation client initialized with model: {}", properties.getModel());
        }

        return generation;
    }

    /**
     * 阿里百炼配置属性
     */
    @Component
    @ConfigurationProperties(prefix = "alibaba.bailian")
    @Data
    public static class BailianProperties {
        /**
         * API密钥 (from application-{profile}.yml)
         */
        private String apiKey;

        /**
         * 模型名称 (默认 qwen-plus)
         */
        private String model = "qwen-plus";

        /**
         * 温度参数 (0.0-1.0，越低越确定性)
         * 默认0.2用于文章摘要生成
         */
        private Float temperature = 0.2f;

        /**
         * 最大输出token数
         * 默认500 (中文约250字，足够摘要场景)
         */
        private Integer maxTokens = 500;

        /**
         * 超时时间(秒)
         */
        private Integer timeoutSeconds = 10;
    }
}

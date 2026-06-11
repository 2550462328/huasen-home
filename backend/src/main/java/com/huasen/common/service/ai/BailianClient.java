package com.huasen.common.service.ai;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.GenerationUsage;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.huasen.common.config.BailianConfig.BailianProperties;
import com.huasen.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 阿里百炼 DashScope SDK 封装客户端
 *
 * 职责：
 * - 封装 DashScope Generation API 调用
 * - 异常转换（SDK 异常 → BusinessException）
 * - 重试策略（瞬时失败重试1次）
 * - 延迟追踪
 *
 * 使用模式（类比 QiniuStorageService）：
 * - SDK wrapper service with translated exceptions
 * - Single-responsibility: only handles API call mechanics
 * - Orchestration services (ArticleSummaryService) compose this client
 */
@Service
public class BailianClient {

    private static final Logger logger = LoggerFactory.getLogger(BailianClient.class);
    private static final int MAX_RETRIES = 1; // 2 attempts total: original + 1 retry

    @Autowired
    private Generation generation;

    @Autowired
    private BailianProperties properties;

    /**
     * AI 调用结果记录
     *
     * @param content AI 生成的文本内容
     * @param inputTokens 输入 token 数
     * @param outputTokens 输出 token 数
     * @param totalTokens 总 token 数
     * @param latencyMs 调用延迟(毫秒)
     * @param finishReason 完成原因: "stop" | "length" | "error"
     * @param requestId DashScope 请求 ID (用于支持工单)
     */
    public record AiResult(
        String content,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        long latencyMs,
        String finishReason,
        String requestId
    ) {}

    /**
     * 调用 DashScope Generation API
     *
     * @param systemPrompt 系统提示词（角色、约束、格式）
     * @param userPrompt 用户提示词（待处理的内容）
     * @return AI 生成结果
     * @throws BusinessException 输入校验失败或 API 调用失败（重试后）
     */
    public AiResult call(String systemPrompt, String userPrompt) {
        // 1. Input validation
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new BusinessException("ERROR", "systemPrompt is required");
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new BusinessException("ERROR", "userPrompt is required");
        }

        // 2. Build messages
        Message systemMessage = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(systemPrompt)
                .build();

        Message userMessage = Message.builder()
                .role(Role.USER.getValue())
                .content(userPrompt)
                .build();

        // 3. Build GenerationParam
        // enableThinking(false): 关闭 qwen3.x 系列的思考模式,避免 reasoning_tokens 占满 max_tokens
        GenerationParam param = GenerationParam.builder()
                .apiKey(properties.getApiKey())
                .model(properties.getModel())
                .messages(List.of(systemMessage, userMessage))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE) // CRITICAL per §3.5 pitfall #2
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .enableThinking(false)
                .build();

        // 4. Retry loop (MAX_RETRIES = 1)
        long startTime = System.currentTimeMillis();
        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                GenerationResult result = generation.call(param);
                long latencyMs = System.currentTimeMillis() - startTime;

                // Extract response content
                String content = result.getOutput().getChoices().get(0).getMessage().getContent();
                String finishReason = result.getOutput().getChoices().get(0).getFinishReason();

                // Extract token counts
                GenerationUsage usage = result.getUsage();
                int inputTokens = usage.getInputTokens();
                int outputTokens = usage.getOutputTokens();
                int totalTokens = inputTokens + outputTokens;

                // Log success
                if (attempt > 0) {
                    logger.info("DashScope call succeeded on retry (attempt {}/{}), latency={}ms, tokens={}",
                            attempt + 1, MAX_RETRIES + 1, latencyMs, totalTokens);
                } else {
                    logger.debug("DashScope call succeeded, latency={}ms, tokens={}, finishReason={}",
                            latencyMs, totalTokens, finishReason);
                }

                return new AiResult(
                    content,
                    inputTokens,
                    outputTokens,
                    totalTokens,
                    latencyMs,
                    finishReason,
                    result.getRequestId()
                );

            } catch (Exception e) {
                lastException = e;

                logger.error("DashScope call failed (attempt {}/{}): {}",
                        attempt + 1, MAX_RETRIES + 1, e.getMessage());

                // If this is not the last attempt, retry
                if (attempt < MAX_RETRIES) {
                    logger.info("Retrying DashScope call (attempt {}/{})", attempt + 2, MAX_RETRIES + 1);
                }
            }
        }

        // 5. After exhausting retries, throw BusinessException
        String errorMessage = String.format(
            "AI service unavailable after %d attempts: %s",
            MAX_RETRIES + 1,
            lastException != null ? lastException.getMessage() : "unknown error"
        );

        logger.error("DashScope call failed after all retries: {}", errorMessage);
        throw new BusinessException("ERROR", errorMessage);
    }

    // Setters for test injection (package-private)
    void setGeneration(Generation generation) {
        this.generation = generation;
    }

    void setProperties(BailianProperties properties) {
        this.properties = properties;
    }
}

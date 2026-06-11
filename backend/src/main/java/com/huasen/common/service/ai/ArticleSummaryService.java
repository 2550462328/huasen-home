package com.huasen.common.service.ai;

import com.huasen.common.constant.RedisKeyConstants;
import com.huasen.common.entity.AiGenerationLog;
import com.huasen.common.repository.AiGenerationLogRepository;
import com.huasen.common.service.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.concurrent.TimeUnit;

/**
 * 文章摘要生成服务
 *
 * 职责：编排 AI 摘要生成的完整流程
 * - 内容清洗（ContentSanitizer）
 * - 缓存查询（Redis）
 * - AI 调用（BailianClient）
 * - 输出校验（AiOutputValidator）
 * - 校验失败重试（最多2次尝试）
 * - 兜底方案（前110字符）
 * - 遥测日志（AiGenerationLog）
 * - 缓存写入（Redis，90天TTL）
 *
 * 非阻塞保证：
 * - 所有异常都被捕获，不向调用方抛出
 * - 总是返回一个有效的摘要字符串（AI输出或兜底方案）
 * - 日志持久化失败不阻塞主流程
 *
 * 缓存策略：
 * - 键格式: ai:article:{prompt_version}:{content_sha256}
 * - TTL: 90天
 * - 版本化失效：修改提示词时递增版本号，旧缓存自动失效
 */
@Service
public class ArticleSummaryService {

    private static final Logger logger = LoggerFactory.getLogger(ArticleSummaryService.class);

    // 校验失败最大重试次数（总共2次尝试）
    private static final int MAX_VALIDATION_RETRIES = 1;

    // 兜底方案字符数（中文字符）
    private static final int FALLBACK_CHARS = 200;

    // 缓存有效期（天）
    private static final int CACHE_TTL_DAYS = 90;

    @Autowired
    private ContentSanitizer contentSanitizer;

    @Autowired
    private BailianClient bailianClient;

    @Autowired
    private AiOutputValidator aiOutputValidator;

    @Autowired
    private RedisService redisService;

    @Autowired
    private AiGenerationLogRepository aiGenerationLogRepository;

    @Autowired
    private com.huasen.common.config.BailianConfig.BailianProperties bailianProperties;

    /**
     * 生成文章摘要
     *
     * 流程：
     * 1. 清洗内容 → 纯文本
     * 2. 构建缓存键
     * 3. 查询缓存 → 命中则返回
     * 4. 调用 AI：
     *    - 构建提示词
     *    - 调用 BailianClient
     *    - 校验输出
     *    - 校验失败则重试一次
     *    - 重试后仍失败则使用兜底方案
     * 5. 持久化遥测日志
     * 6. 校验通过则写入缓存
     * 7. 返回摘要字符串
     *
     * @param articleId TinyBlogPost ID（nullable，用于日志关联）
     * @param rawContent 文章正文（HTML/Markdown）
     * @return 摘要字符串（70-110字符，或兜底方案）
     */
    public String generateSummary(Long articleId, String rawContent) {
        // 1. 清洗内容
        String sanitized = contentSanitizer.sanitizeForPrompt(rawContent);
        if (sanitized.isBlank()) {
            logger.warn("Article content empty after sanitize, articleId={}", articleId);
            return "";
        }

        // 2. 构建缓存键
        String contentHash = DigestUtils.md5DigestAsHex(sanitized.getBytes());
        String cacheKey = RedisKeyConstants.AI_CACHE_PREFIX + "article:" +
                          PromptTemplates.ARTICLE_SUMMARY_PROMPT_VERSION + ":" + contentHash;

        // 3. 查询缓存
        String cached = redisService.get(cacheKey);
        if (cached != null) {
            logger.info("Cache hit for articleId={}, hash={}", articleId, contentHash.substring(0, 8));
            persistLogAsync(articleId, contentHash, cached, null, true, false, 0, 0, 0, true);
            return cached;
        }

        // 4. 调用 AI（带校验重试）
        String summary = null;
        boolean validationPass = false;
        boolean fallbackUsed = false;
        BailianClient.AiResult aiResult = null;

        for (int attempt = 0; attempt <= MAX_VALIDATION_RETRIES; attempt++) {
            try {
                String systemPrompt = PromptTemplates.ARTICLE_SUMMARY_SYSTEM_V1;
                String userPrompt = PromptTemplates.articleSummaryUser(sanitized);

                aiResult = bailianClient.call(systemPrompt, userPrompt);
                AiOutputValidator.ValidationResult validation =
                    aiOutputValidator.validate(aiResult.content(), aiResult.finishReason());

                if (validation.valid()) {
                    summary = validation.sanitized();
                    validationPass = true;
                    logger.info("AI summary generated for articleId={}, attempt={}/{}, tokens={}",
                                articleId, attempt + 1, MAX_VALIDATION_RETRIES + 1, aiResult.totalTokens());
                    break;
                } else {
                    logger.warn("AI output validation failed (attempt {}/{}), reason={}, articleId={}",
                                attempt + 1, MAX_VALIDATION_RETRIES + 1, validation.reason(), articleId);

                    // TOO_LONG: AI 内容本身可用,直接截到句号边界,跳过重试(避免再花 4s 调一次)
                    if ("TOO_LONG".equals(validation.reason())) {
                        String truncated = truncateToSentence(validation.sanitized());
                        if (truncated != null) {
                            summary = truncated;
                            validationPass = true;
                            logger.info("AI summary truncated to sentence boundary, articleId={}, len={}",
                                        articleId, truncated.codePointCount(0, truncated.length()));
                            break;
                        }
                        // 截不到句号(极少见),硬截到 maxChars 也比降级到文章开头强
                        String hardTruncated = hardTruncate(validation.sanitized());
                        if (hardTruncated != null) {
                            summary = hardTruncated;
                            validationPass = true;
                            logger.info("AI summary hard-truncated, articleId={}, len={}",
                                        articleId, hardTruncated.codePointCount(0, hardTruncated.length()));
                            break;
                        }
                    }

                    if (attempt == MAX_VALIDATION_RETRIES) {
                        summary = buildFallbackSummary(sanitized);
                        fallbackUsed = true;
                        logger.warn("Using fallback summary after validation failures, articleId={}", articleId);
                    }
                }
            } catch (Exception e) {
                logger.error("AI call exception (attempt {}/{}), articleId={}: {}",
                             attempt + 1, MAX_VALIDATION_RETRIES + 1, articleId, e.getMessage());
                if (attempt == MAX_VALIDATION_RETRIES) {
                    // 重试耗尽，使用兜底方案
                    summary = buildFallbackSummary(sanitized);
                    fallbackUsed = true;
                }
            }
        }

        // 5. 持久化遥测日志（fire-and-forget）
        int inputTokens = aiResult != null ? aiResult.inputTokens() : 0;
        int outputTokens = aiResult != null ? aiResult.outputTokens() : 0;
        long latencyMs = aiResult != null ? aiResult.latencyMs() : 0;
        String requestId = aiResult != null ? aiResult.requestId() : null;

        persistLogAsync(articleId, contentHash, summary, requestId, false, fallbackUsed,
                        inputTokens, outputTokens, latencyMs, validationPass);

        // 6. 缓存写入（仅校验通过时）
        if (validationPass && summary != null) {
            try {
                redisService.set(cacheKey, summary, CACHE_TTL_DAYS, TimeUnit.DAYS);
            } catch (Exception e) {
                logger.warn("Redis cache write failed for articleId={}: {}", articleId, e.getMessage());
            }
        }

        // 7. 返回摘要
        return summary != null ? summary : buildFallbackSummary(sanitized);
    }

    /**
     * 将超长的 AI 摘要截断到句号边界。
     *
     * 在 [MIN, MAX] 字符范围内寻找最后一个句末标点（。！？），
     * 截断到该句结束。若范围内无句末标点，返回 null（交给上层走兜底）。
     *
     * @param aiSummary AI 生成的（超长）摘要，已净化
     * @return 截断到完整句子的摘要，或 null（无合适截断点）
     */
    private String truncateToSentence(String aiSummary) {
        if (aiSummary == null || aiSummary.isBlank()) {
            return null;
        }
        int minChars = 180;
        int maxChars = 320;
        int total = aiSummary.codePointCount(0, aiSummary.length());
        // 扫描的上界：不超过 maxChars 也不超过全文
        int scanLimit = Math.min(maxChars, total);
        int lastSentenceEnd = -1;
        for (int cp = 0; cp < scanLimit; cp++) {
            int idx = aiSummary.offsetByCodePoints(0, cp);
            char c = aiSummary.charAt(idx);
            if ((c == '。' || c == '！' || c == '？') && (cp + 1) >= minChars) {
                lastSentenceEnd = cp + 1; // 含标点
            }
        }
        if (lastSentenceEnd < 0) {
            return null;
        }
        int endIdx = aiSummary.offsetByCodePoints(0, lastSentenceEnd);
        return aiSummary.substring(0, endIdx);
    }

    /**
     * 硬截断:在 [minChars, maxChars] 范围内找不到句号时,直接截到 maxChars。
     * 比降级到文章开头(buildFallbackSummary)更接近 AI 摘要语义。
     */
    private String hardTruncate(String aiSummary) {
        if (aiSummary == null || aiSummary.isBlank()) {
            return null;
        }
        int maxChars = 320;
        int total = aiSummary.codePointCount(0, aiSummary.length());
        if (total <= maxChars) {
            return aiSummary; // 理论不会进来,防御性
        }
        int endIdx = aiSummary.offsetByCodePoints(0, maxChars);
        return aiSummary.substring(0, endIdx);
    }

    /**
     * 构建兜底摘要（取清洗后内容的前110个字符）
     *
     * 使用 codePointCount 正确处理中文字符
     * （避免按字节截断导致的乱码）
     *
     * @param sanitized 清洗后的纯文本内容
     * @return 前110个字符，或全文（如果不足110字符）
     */
    private String buildFallbackSummary(String sanitized) {
        if (sanitized.isBlank()) {
            return "";
        }

        int totalCodePoints = sanitized.codePointCount(0, sanitized.length());
        int endCodePoints = Math.min(totalCodePoints, FALLBACK_CHARS);

        // 将 code-point 计数转换为字符串索引
        int charIndex = sanitized.offsetByCodePoints(0, endCodePoints);
        return sanitized.substring(0, charIndex);
    }

    /**
     * 异步持久化遥测日志（fire-and-forget模式）
     *
     * 日志保存失败不阻塞主流程，仅记录警告
     *
     * @param articleId TinyBlogPost ID
     * @param inputHash 内容 SHA-256 哈希
     * @param output 生成的摘要文本
     * @param requestId DashScope 请求 ID
     * @param cacheHit 是否缓存命中
     * @param fallbackUsed 是否使用兜底方案
     * @param inputTokens 输入 token 数
     * @param outputTokens 输出 token 数
     * @param latencyMs 延迟（毫秒）
     * @param validationPass 是否通过校验
     */
    private void persistLogAsync(Long articleId, String inputHash, String output, String requestId,
                                  boolean cacheHit, boolean fallbackUsed, int inputTokens,
                                  int outputTokens, long latencyMs, boolean validationPass) {
        try {
            AiGenerationLog log = new AiGenerationLog();
            log.setFeature("tinyblog_summary");
            log.setRefId(articleId != null ? articleId.toString() : null);
            log.setInputHash(inputHash);
            log.setModel(bailianProperties.getModel());
            log.setRequestId(requestId);
            log.setPromptVersion(PromptTemplates.ARTICLE_SUMMARY_PROMPT_VERSION);
            log.setAiOutput(output != null ? output : "");
            log.setFinishReason(cacheHit ? "cache_hit" : (fallbackUsed ? "fallback" : "stop"));
            log.setInputTokens(inputTokens);
            log.setOutputTokens(outputTokens);
            log.setLatencyMs((int) latencyMs);
            log.setValidationPass(validationPass);
            log.setFallbackUsed(fallbackUsed);

            aiGenerationLogRepository.save(log);
        } catch (Exception e) {
            logger.warn("Failed to persist AI generation log (not blocking operation): {}", e.getMessage());
        }
    }
}

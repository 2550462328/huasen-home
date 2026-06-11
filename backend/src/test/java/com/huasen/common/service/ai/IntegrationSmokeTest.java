package com.huasen.common.service.ai;

import com.huasen.common.entity.AiGenerationLog;
import com.huasen.common.repository.AiGenerationLogRepository;
import com.huasen.common.service.RedisService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration smoke test for 阿里百炼 AI stack.
 *
 * <p>This test makes REAL calls to DashScope API (consumes tokens/quota).
 * It is @Disabled by default and should be run manually when:
 * <ul>
 *   <li>Verifying DashScope API key configuration</li>
 *   <li>Testing prompt changes before deployment</li>
 *   <li>Debugging SDK integration issues</li>
 * </ul>
 *
 * <p><b>How to run:</b>
 * <ol>
 *   <li>Ensure application-dev.yml has valid alibaba.bailian.api-key</li>
 *   <li>Remove @Disabled annotation temporarily OR run with:
 *       {@code mvn test -Dtest=IntegrationSmokeTest}</li>
 *   <li>Check console output for "✅ Integration smoke test passed!" log</li>
 *   <li>Re-enable @Disabled before committing</li>
 * </ol>
 *
 * <p><b>Expected behavior:</b>
 * <ul>
 *   <li>First run: cache miss, AI call to DashScope, summary returned (70-110 chars)</li>
 *   <li>Second run (same input): cache hit, no AI call, cached summary returned</li>
 *   <li>Redis cache + ai_generation_log row persisted</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("dev")  // Use dev profile (localhost Redis + MySQL + DashScope dev API key)
@Disabled("Manual execution only — consumes DashScope API quota")
public class IntegrationSmokeTest {

    @Autowired
    private ArticleSummaryService articleSummaryService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private AiGenerationLogRepository aiGenerationLogRepository;

    @Autowired
    private ContentSanitizer contentSanitizer;

    @Test
    void testFullAiStack_RealDashScopeCall() {
        // 测试文章内容 (150 chars, typical TinyBlog post excerpt)
        String testArticle = """
            本文介绍Spring Boot 3.x集成阿里百炼大模型的实践经验。
            通过DashScope SDK调用通义千问Qwen-Plus模型，实现文章摘要自动生成功能。
            主要步骤包括：添加Maven依赖、配置API密钥、创建Service层封装、设计提示词模板、实现输出校验与重试机制。
            生产环境运行3个月，平均响应时间2.3秒，摘要质量满意度达92%。推荐用于中文内容的自动化处理场景。
            """;

        // 清除可能存在的缓存 (确保测试AI调用)
        String sanitized = contentSanitizer.sanitizeForPrompt(testArticle);
        String contentHash = DigestUtils.md5DigestAsHex(sanitized.getBytes(StandardCharsets.UTF_8));
        String cacheKey = "ai:article:v1:" + contentHash;
        redisService.delete(cacheKey);

        // Act: 调用AI服务生成摘要
        String summary = articleSummaryService.generateSummary(999L, testArticle);

        // Assert: 验证返回结果
        assertNotNull(summary, "Summary should not be null");
        assertFalse(summary.isBlank(), "Summary should not be blank");

        // 验证长度在D1范围内 (70-110 chars, or fallback length if AI failed)
        int charCount = summary.codePointCount(0, summary.length());
        assertTrue(charCount >= 70 && charCount <= 120,
                   "Summary char count should be in [70, 120] range, got: " + charCount);

        // 验证无禁止格式 (D5)
        assertFalse(summary.startsWith("摘要："), "Summary should not have prefix");
        assertFalse(summary.startsWith("\""), "Summary should not be quoted");
        assertFalse(summary.contains("**"), "Summary should not have Markdown bold");

        // 验证缓存写入
        String cached = redisService.get(cacheKey);
        assertEquals(summary, cached, "Summary should be cached in Redis");

        // 验证数据库日志
        Optional<AiGenerationLog> logOpt = aiGenerationLogRepository
                .findFirstByInputHashAndPromptVersionOrderByCreatedAtDesc(contentHash, "v1");
        assertTrue(logOpt.isPresent(), "AI generation log should exist");
        AiGenerationLog log = logOpt.get();
        assertEquals("tinyblog_summary", log.getFeature());
        assertEquals("qwen-plus", log.getModel());
        assertNotNull(log.getAiOutput());
        assertTrue(log.getLatencyMs() > 0, "Latency should be recorded");

        System.out.println("✅ Integration smoke test passed!");
        System.out.println("Summary: " + summary);
        System.out.println("Tokens: " + log.getInputTokens() + " in, " + log.getOutputTokens() + " out");
        System.out.println("Latency: " + log.getLatencyMs() + " ms");
    }

    @Test
    void testCacheHit_SecondCall() {
        // 同样的测试文章，第二次调用应该命中缓存
        String testArticle = """
            本文介绍Spring Boot 3.x集成阿里百炼大模型的实践经验。
            通过DashScope SDK调用通义千问Qwen-Plus模型，实现文章摘要自动生成功能。
            """;

        // First call (may hit cache if previous test ran)
        String summary1 = articleSummaryService.generateSummary(999L, testArticle);

        // Second call (should definitely hit cache)
        long startTime = System.currentTimeMillis();
        String summary2 = articleSummaryService.generateSummary(999L, testArticle);
        long elapsed = System.currentTimeMillis() - startTime;

        // Assert: 结果一致且极快 (缓存命中)
        assertEquals(summary1, summary2, "Cache should return same summary");
        assertTrue(elapsed < 100, "Cache hit should be < 100ms, got: " + elapsed + "ms");

        System.out.println("✅ Cache hit test passed!");
        System.out.println("Cache response time: " + elapsed + " ms");
    }
}

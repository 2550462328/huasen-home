package com.huasen.common.service.ai;

import com.huasen.common.entity.AiGenerationLog;
import com.huasen.common.exception.BusinessException;
import com.huasen.common.repository.AiGenerationLogRepository;
import com.huasen.common.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ArticleSummaryService 单元测试
 *
 * 测试场景:
 * 1. 缓存命中 - 直接返回缓存结果，不调用AI
 * 2. 缓存未命中 + AI成功 + 校验通过 - 写入缓存并返回AI输出
 * 3. 缓存未命中 + AI校验失败 - 重试一次后成功
 * 4. 缓存未命中 + AI校验失败两次 - 使用兜底方案（前110字符）
 * 5. 缓存未命中 + AI异常 - 使用兜底方案
 * 6. 所有调用都记录遥测日志
 * 7. 兜底方案正确处理中文字符计数
 */
@ExtendWith(MockitoExtension.class)
class ArticleSummaryServiceTest {

    @Mock
    private ContentSanitizer contentSanitizer;

    @Mock
    private BailianClient bailianClient;

    @Mock
    private AiOutputValidator aiOutputValidator;

    @Mock
    private RedisService redisService;

    @Mock
    private AiGenerationLogRepository aiGenerationLogRepository;

    @InjectMocks
    private ArticleSummaryService service;

    private static final Long TEST_ARTICLE_ID = 123L;
    private static final String TEST_RAW_CONTENT = "<p>这是一篇测试文章，包含HTML标签和**Markdown**语法。</p>";
    private static final String TEST_SANITIZED_CONTENT = "这是一篇测试文章，包含HTML标签和Markdown语法。";

    @BeforeEach
    void setUp() {
        // 默认mock sanitizer行为
        when(contentSanitizer.sanitizeForPrompt(anyString())).thenReturn(TEST_SANITIZED_CONTENT);
    }

    /**
     * Test 1: 缓存命中 - 返回缓存结果，不调用AI
     */
    @Test
    void testCacheHit_ReturnsCachedSummary_NoAiCall() {
        // Given: Redis缓存中已有结果
        String cachedSummary = "这是一篇缓存的摘要，描述了文章的核心内容，长度符合70-110字符的要求，无前缀无引号无Markdown格式。";
        when(redisService.get(anyString())).thenReturn(cachedSummary);

        // When
        String result = service.generateSummary(TEST_ARTICLE_ID, TEST_RAW_CONTENT);

        // Then
        assertEquals(cachedSummary, result);

        // Verify: AI client未被调用
        verify(bailianClient, never()).call(anyString(), anyString());

        // Verify: 记录了缓存命中的遥测日志
        ArgumentCaptor<AiGenerationLog> logCaptor = ArgumentCaptor.forClass(AiGenerationLog.class);
        verify(aiGenerationLogRepository, times(1)).save(logCaptor.capture());
        AiGenerationLog log = logCaptor.getValue();
        assertEquals("tinyblog_summary", log.getFeature());
        assertEquals(TEST_ARTICLE_ID.toString(), log.getRefId());
        assertEquals("cache_hit", log.getFinishReason());
        assertNotNull(log.getInputHash());
    }

    /**
     * Test 2: 缓存未命中 + AI成功 + 校验通过 - 写入缓存并返回AI输出
     */
    @Test
    void testCacheMiss_AiSuccess_ValidationPass_CachesAndReturnsAiOutput() {
        // Given: 缓存未命中
        when(redisService.get(anyString())).thenReturn(null);

        // Given: AI调用成功
        String aiOutput = "这是AI生成的摘要，准确概括了文章的核心观点，长度适中，格式干净，适合展示在列表页作为文章预览。";
        BailianClient.AiResult aiResult = new BailianClient.AiResult(
            aiOutput, 200, 50, 250, 1200L, "stop", "req-12345"
        );
        when(bailianClient.call(anyString(), anyString())).thenReturn(aiResult);

        // Given: 校验通过
        AiOutputValidator.ValidationResult validResult = AiOutputValidator.ValidationResult.pass(aiOutput);
        when(aiOutputValidator.validate(aiOutput, "stop")).thenReturn(validResult);

        // When
        String result = service.generateSummary(TEST_ARTICLE_ID, TEST_RAW_CONTENT);

        // Then
        assertEquals(aiOutput, result);

        // Verify: AI被调用一次
        verify(bailianClient, times(1)).call(anyString(), anyString());

        // Verify: 缓存被写入，TTL为90天
        verify(redisService, times(1)).set(anyString(), eq(aiOutput), eq(90L), eq(TimeUnit.DAYS));

        // Verify: 记录了AI调用的遥测日志
        ArgumentCaptor<AiGenerationLog> logCaptor = ArgumentCaptor.forClass(AiGenerationLog.class);
        verify(aiGenerationLogRepository, times(1)).save(logCaptor.capture());
        AiGenerationLog log = logCaptor.getValue();
        assertEquals("tinyblog_summary", log.getFeature());
        assertEquals(TEST_ARTICLE_ID.toString(), log.getRefId());
        assertEquals("stop", log.getFinishReason());
        assertEquals(200, log.getInputTokens());
        assertEquals(50, log.getOutputTokens());
        assertEquals(1200, log.getLatencyMs());
        assertTrue(log.getValidationPass());
        assertFalse(log.getFallbackUsed());
        assertEquals("req-12345", log.getRequestId());
    }

    /**
     * Test 3: 缓存未命中 + AI校验失败 → 重试一次 → 第二次成功
     */
    @Test
    void testCacheMiss_ValidationFails_RetrySucceeds() {
        // Given: 缓存未命中
        when(redisService.get(anyString())).thenReturn(null);

        // Given: 第一次AI调用返回无效输出
        String invalidOutput = "摘要：这是一个带前缀的摘要。";
        BailianClient.AiResult firstResult = new BailianClient.AiResult(
            invalidOutput, 200, 30, 230, 1100L, "stop", "req-11111"
        );

        // Given: 第二次AI调用返回有效输出
        String validOutput = "这是修正后的AI摘要，去除了前缀，格式规范，长度适中，内容准确反映了文章的核心论点和关键结论。";
        BailianClient.AiResult secondResult = new BailianClient.AiResult(
            validOutput, 200, 55, 255, 1150L, "stop", "req-22222"
        );

        when(bailianClient.call(anyString(), anyString()))
            .thenReturn(firstResult)
            .thenReturn(secondResult);

        // Given: 第一次校验失败，第二次成功
        AiOutputValidator.ValidationResult invalidResult =
            AiOutputValidator.ValidationResult.fail("HAS_PREFIX", "这是一个带前缀的摘要。");
        AiOutputValidator.ValidationResult validResult =
            AiOutputValidator.ValidationResult.pass(validOutput);

        when(aiOutputValidator.validate(invalidOutput, "stop")).thenReturn(invalidResult);
        when(aiOutputValidator.validate(validOutput, "stop")).thenReturn(validResult);

        // When
        String result = service.generateSummary(TEST_ARTICLE_ID, TEST_RAW_CONTENT);

        // Then
        assertEquals(validOutput, result);

        // Verify: AI被调用两次
        verify(bailianClient, times(2)).call(anyString(), anyString());

        // Verify: 缓存被写入（因为最终成功）
        verify(redisService, times(1)).set(anyString(), eq(validOutput), eq(90L), eq(TimeUnit.DAYS));

        // Verify: 记录了遥测日志
        verify(aiGenerationLogRepository, times(1)).save(any(AiGenerationLog.class));
    }

    /**
     * Test: 校验失败两次且原因为 TOO_LONG → 截断到句号边界（而非取文章开头兜底）
     */
    @Test
    void testCacheMiss_TooLong_TruncatesToSentence() {
        when(redisService.get(anyString())).thenReturn(null);

        // 一个超长摘要：3 句，整体 >170 字，首句结束于约 100 字
        String s1 = "MyBatis提供一级缓存与二级缓存两种机制以减轻数据库压力并提升查询效率而一级缓存基于会话二级缓存基于命名空间各有适用范围和生命周期差异需要根据场景选择。";
        String s2 = "二级缓存在多表关联或分布式部署下存在脏读与数据不一致的风险因此必须谨慎评估其副作用并配置合理的刷新策略来规避问题保证数据正确。";
        String s3 = "实际工程中更推荐引入Redis等集中式缓存替代二级缓存以获得更强的一致性保证和可控的失效语义。";
        String tooLong = s1 + s2 + s3;

        BailianClient.AiResult longResult = new BailianClient.AiResult(
            tooLong, 2800, 180, 2980, 3000L, "stop", "req-long"
        );
        // 两次调用都返回同一个超长输出
        when(bailianClient.call(anyString(), anyString())).thenReturn(longResult);

        AiOutputValidator.ValidationResult tooLongResult =
            AiOutputValidator.ValidationResult.fail("TOO_LONG", tooLong);
        when(aiOutputValidator.validate(tooLong, "stop")).thenReturn(tooLongResult);

        // When
        String result = service.generateSummary(TEST_ARTICLE_ID, TEST_RAW_CONTENT);

        // Then: 结果是 AI 摘要的句子前缀（以句号结尾），不是文章开头兜底
        assertTrue(result.startsWith("MyBatis提供一级缓存"),
                   "应保留 AI 摘要内容，而非取文章开头");
        assertTrue(result.endsWith("。"), "应截断到完整句子");
        int len = result.codePointCount(0, result.length());
        assertTrue(len >= 70 && len <= 170, "截断后长度应在 [70,170]，实际: " + len);

        // Verify: TOO_LONG 截断视为成功，会写入缓存
        verify(redisService, times(1)).set(anyString(), eq(result), eq(90L), eq(TimeUnit.DAYS));
    }

    /**
     * Test 4: 缓存未命中 + AI校验失败两次 → 兜底方案（前110字符）
     */
    @Test
    void testCacheMiss_ValidationFailsTwice_ReturnsFallback() {
        // Given: 缓存未命中
        when(redisService.get(anyString())).thenReturn(null);

        // Given: AI两次调用都返回无效输出
        String invalidOutput1 = "「这是带引号的摘要」";
        String invalidOutput2 = "## 这是带Markdown标题的摘要";

        BailianClient.AiResult result1 = new BailianClient.AiResult(
            invalidOutput1, 200, 20, 220, 1100L, "stop", "req-33333"
        );
        BailianClient.AiResult result2 = new BailianClient.AiResult(
            invalidOutput2, 200, 25, 225, 1120L, "stop", "req-44444"
        );

        when(bailianClient.call(anyString(), anyString()))
            .thenReturn(result1)
            .thenReturn(result2);

        // Given: 两次校验都失败
        AiOutputValidator.ValidationResult invalid1 =
            AiOutputValidator.ValidationResult.fail("HAS_QUOTES", "这是带引号的摘要");
        AiOutputValidator.ValidationResult invalid2 =
            AiOutputValidator.ValidationResult.fail("HAS_MARKDOWN", "这是带Markdown标题的摘要");

        when(aiOutputValidator.validate(invalidOutput1, "stop")).thenReturn(invalid1);
        when(aiOutputValidator.validate(invalidOutput2, "stop")).thenReturn(invalid2);

        // When
        String result = service.generateSummary(TEST_ARTICLE_ID, TEST_RAW_CONTENT);

        // Then: 返回sanitized content的前110个字符（兜底方案）
        assertNotNull(result);
        assertTrue(result.length() <= 110);
        assertTrue(result.startsWith("这是一篇测试文章"));

        // Verify: AI被调用两次
        verify(bailianClient, times(2)).call(anyString(), anyString());

        // Verify: 缓存未被写入（因为使用了兜底方案）
        verify(redisService, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        // Verify: 记录了遥测日志，标记fallbackUsed=true
        ArgumentCaptor<AiGenerationLog> logCaptor = ArgumentCaptor.forClass(AiGenerationLog.class);
        verify(aiGenerationLogRepository, times(1)).save(logCaptor.capture());
        AiGenerationLog log = logCaptor.getValue();
        assertTrue(log.getFallbackUsed());
        assertFalse(log.getValidationPass());
    }

    /**
     * Test 5: 缓存未命中 + AI异常 → 兜底方案
     */
    @Test
    void testCacheMiss_AiException_ReturnsFallback() {
        // Given: 缓存未命中
        when(redisService.get(anyString())).thenReturn(null);

        // Given: AI调用抛出异常（模拟网络故障或API限流）
        when(bailianClient.call(anyString(), anyString()))
            .thenThrow(new BusinessException("ERROR", "AI service unavailable"));

        // When
        String result = service.generateSummary(TEST_ARTICLE_ID, TEST_RAW_CONTENT);

        // Then: 返回兜底方案，不抛异常
        assertNotNull(result);
        assertTrue(result.length() <= 110);
        assertTrue(result.startsWith("这是一篇测试文章"));

        // Verify: AI被调用两次（原始调用 + 1次重试）
        verify(bailianClient, times(2)).call(anyString(), anyString());

        // Verify: 记录了遥测日志，标记fallbackUsed=true
        ArgumentCaptor<AiGenerationLog> logCaptor = ArgumentCaptor.forClass(AiGenerationLog.class);
        verify(aiGenerationLogRepository, times(1)).save(logCaptor.capture());
        AiGenerationLog log = logCaptor.getValue();
        assertTrue(log.getFallbackUsed());
        assertEquals("fallback", log.getFinishReason());
        assertEquals(0, log.getInputTokens());
        assertEquals(0, log.getOutputTokens());
    }

    /**
     * Test 6: 所有调用都记录遥测日志（已在上述测试中验证）
     * 此测试作为补充，显式验证日志记录不阻塞主流程
     */
    @Test
    void testTelemetryLogging_NeverBlocksMainFlow() {
        // Given: 缓存命中
        when(redisService.get(anyString())).thenReturn("缓存的摘要");

        // Given: 日志保存抛出异常（模拟数据库故障）
        when(aiGenerationLogRepository.save(any(AiGenerationLog.class)))
            .thenThrow(new RuntimeException("Database connection failed"));

        // When: 即使日志保存失败，主流程仍应返回结果
        String result = service.generateSummary(TEST_ARTICLE_ID, TEST_RAW_CONTENT);

        // Then: 不抛异常，返回缓存的结果
        assertEquals("缓存的摘要", result);
    }

    /**
     * Test 7: 兜底方案正确处理中文字符计数（使用codePointCount）
     */
    @Test
    void testFallback_HandlesChineseCharactersCorrectly() {
        // Given: 准备一个200字符的中文内容
        String longChineseContent = "这是一篇很长的中文文章内容。".repeat(20); // 200个中文字符
        when(contentSanitizer.sanitizeForPrompt(anyString())).thenReturn(longChineseContent);

        // Given: 缓存未命中
        when(redisService.get(anyString())).thenReturn(null);

        // Given: AI调用抛出异常，触发兜底方案
        when(bailianClient.call(anyString(), anyString()))
            .thenThrow(new BusinessException("ERROR", "API error"));

        // When
        String result = service.generateSummary(TEST_ARTICLE_ID, TEST_RAW_CONTENT);

        // Then: 兜底方案返回前110个中文字符（不是110字节）
        assertNotNull(result);
        int charCount = result.codePointCount(0, result.length());
        assertTrue(charCount <= 110, "Fallback should return at most 110 characters, got " + charCount);
        assertTrue(result.startsWith("这是一篇很长的中文文章内容"));
    }

    /**
     * Test 8: 空内容或清洗后为空的情况
     */
    @Test
    void testEmptyContent_ReturnsEmptyString() {
        // Given: 清洗后内容为空
        when(contentSanitizer.sanitizeForPrompt(anyString())).thenReturn("");

        // When
        String result = service.generateSummary(TEST_ARTICLE_ID, "   ");

        // Then: 返回空字符串，不调用AI
        assertEquals("", result);
        verify(bailianClient, never()).call(anyString(), anyString());
    }
}

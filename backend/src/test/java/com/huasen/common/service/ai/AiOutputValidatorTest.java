package com.huasen.common.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AiOutputValidator
 * Tests D1 (length 70-110 chars) and D5 (no prefix/quotes/Markdown) validation rules
 */
class AiOutputValidatorTest {

    private AiOutputValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AiOutputValidator();
    }

    @Test
    void testValidSummary() {
        // 80 Chinese characters - valid summary
        String output = "本文介绍Spring Boot集成百炼AI的完整方案。涵盖SDK配置、Bean注册、提示词设计和输出校验四大模块，并提供缓存优化策略，帮助开发者快速构建智能应用。";
        AiOutputValidator.ValidationResult result = validator.validate(output, "stop");

        assertTrue(result.valid());
        assertNull(result.reason());
        assertEquals(output, result.sanitized());
    }

    @Test
    void testTooShort() {
        // 65 Chinese characters - below minimum of 70
        String output = "本文介绍了Spring Boot与百炼AI的集成方法。包括SDK配置、提示词设计和输出校验等核心内容。适合Java开发者快速上手AI功能。";
        AiOutputValidator.ValidationResult result = validator.validate(output, "stop");

        assertFalse(result.valid());
        assertEquals("TOO_SHORT", result.reason());
        assertEquals(output, result.sanitized());
    }

    @Test
    void testTooLong() {
        // 120 Chinese characters - exceeds maximum of 110
        String output = "这是一篇全面介绍Spring Boot集成阿里百炼大模型的深度技术文章。作者从零开始详细讲解了DashScope SDK的安装配置、Maven依赖管理、Spring Bean注册机制、提示词工程设计原则、输出质量校验流程以及缓存优化策略等六大关键技术模块。文章结构清晰、代码示例丰富，特别适合希望在Java企业级项目中快速接入AI生成能力的中高级开发者系统学习和实践参考。";
        AiOutputValidator.ValidationResult result = validator.validate(output, "stop");

        assertFalse(result.valid());
        assertEquals("TOO_LONG", result.reason());
        assertEquals(output, result.sanitized());
    }

    @Test
    void testTruncatedOutput() {
        // finish_reason=length indicates output was cut off at max_tokens
        String output = "这是一篇关于Spring Boot集成阿里百炼大模型的技术文章。作者详细介绍了DashScope SDK的使用方法，包括依赖配置、Bean注册、提示词设计和输出校验四个关键步骤。";
        AiOutputValidator.ValidationResult result = validator.validate(output, "length");

        assertFalse(result.valid());
        assertEquals("TRUNCATED", result.reason());
    }

    @Test
    void testHasPrefix() {
        // Summary with prefix - should fail
        String output = "摘要：本文介绍Spring Boot集成百炼AI的完整方案。涵盖SDK配置、Bean注册、提示词设计和输出校验四大模块，并提供缓存优化策略，帮助开发者快速构建智能应用。";
        AiOutputValidator.ValidationResult result = validator.validate(output, "stop");

        assertFalse(result.valid());
        assertEquals("HAS_PREFIX", result.reason());
        assertFalse(result.sanitized().startsWith("摘要："));
    }

    @Test
    void testHasQuotes() {
        // Summary wrapped in quotes - should fail
        String output = "\"本文介绍Spring Boot集成百炼AI的完整方案。涵盖SDK配置、Bean注册、提示词设计和输出校验四大模块，并提供缓存优化策略，帮助开发者快速构建智能应用。\"";
        AiOutputValidator.ValidationResult result = validator.validate(output, "stop");

        assertFalse(result.valid());
        assertEquals("HAS_QUOTES", result.reason());
        assertFalse(result.sanitized().startsWith("\""));
        assertFalse(result.sanitized().endsWith("\""));
    }

    @Test
    void testHasMarkdown() {
        // Summary with Markdown bold syntax - should fail
        String output = "本文介绍**Spring Boot**集成百炼AI的完整方案。涵盖SDK配置、Bean注册、提示词设计和输出校验四大模块，并提供缓存优化策略，帮助开发者快速构建智能应用。";
        AiOutputValidator.ValidationResult result = validator.validate(output, "stop");

        assertFalse(result.valid());
        assertEquals("HAS_MARKDOWN", result.reason());
    }

    @Test
    void testValidAfterSanitization() {
        // Summary with cosmetic prefix that gets stripped
        String output = "简介：本文介绍Spring Boot集成百炼AI的完整方案。涵盖SDK配置、Bean注册、提示词设计和输出校验四大模块，并提供缓存优化策略，帮助开发者快速构建智能应用。";
        AiOutputValidator.ValidationResult result = validator.validate(output, "stop");

        assertFalse(result.valid());
        assertEquals("HAS_PREFIX", result.reason());
    }

    @Test
    void testEmptyInput() {
        AiOutputValidator.ValidationResult result = validator.validate("", "stop");

        assertFalse(result.valid());
        assertEquals("EMPTY", result.reason());
    }

    @Test
    void testNullInput() {
        AiOutputValidator.ValidationResult result = validator.validate(null, "stop");

        assertFalse(result.valid());
        assertEquals("EMPTY", result.reason());
    }

    @Test
    void testWhitespaceOnlyInput() {
        AiOutputValidator.ValidationResult result = validator.validate("   \n\t  ", "stop");

        assertFalse(result.valid());
        assertEquals("EMPTY", result.reason());
    }

    @Test
    void testMultiplePrefixFormats() {
        String[] prefixes = {"摘要：", "简介：", "描述：", "总结：", "概述："};

        for (String prefix : prefixes) {
            String output = prefix + "本文介绍Spring Boot集成百炼AI的完整方案。涵盖SDK配置、Bean注册、提示词设计和输出校验四大模块，并提供缓存优化策略，帮助开发者快速构建智能应用。";
            AiOutputValidator.ValidationResult result = validator.validate(output, "stop");

            assertFalse(result.valid(), "Should fail for prefix: " + prefix);
            assertEquals("HAS_PREFIX", result.reason());
        }
    }

    @Test
    void testBoundaryLength70() {
        // Exactly 70 characters - should pass
        String output = "本文介绍Spring Boot与百炼AI集成方案，涵盖SDK配置、Bean注册、提示词设计和输出校验模块，帮助Java开发者快速构建AI应用";
        int charCount = output.codePointCount(0, output.length());
        assertEquals(70, charCount, "Test data should be exactly 70 chars");

        AiOutputValidator.ValidationResult result = validator.validate(output, "stop");

        assertTrue(result.valid());
        assertNull(result.reason());
    }

    @Test
    void testBoundaryLength110() {
        // Exactly 110 characters - should pass
        String output = "本文系统介绍Spring Boot框架与百炼大模型深度集成的完整技术方案。详细讲解SDK配置使用、依赖管理、Bean注册、提示词工程设计、输出质量校验以及缓存优化等关键模块，为企业级Java应用智能化转型升级提供实战指导";
        int charCount = output.codePointCount(0, output.length());
        assertEquals(110, charCount, "Test data should be exactly 110 chars");

        AiOutputValidator.ValidationResult result = validator.validate(output, "stop");

        assertTrue(result.valid());
        assertNull(result.reason());
    }
}

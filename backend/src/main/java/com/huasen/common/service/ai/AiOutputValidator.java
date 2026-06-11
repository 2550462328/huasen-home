package com.huasen.common.service.ai;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI输出校验器
 * 实施质量维度 D1 (长度契合展示位) 和 D5 (纯净中文输出) 的校验规则
 *
 * D1: 文章摘要长度 70-110 个中文字符
 * D5: 无前缀、无引号包裹、无Markdown语法、无元信息说明
 *
 * @see com.huasen.common.service.ai.AiOutputValidator.ValidationResult
 */
@Component
public class AiOutputValidator {

    // D1 长度约束常量
    private static final int ARTICLE_SUMMARY_MIN_CHARS = 180;
    private static final int ARTICLE_SUMMARY_MAX_CHARS = 320;

    // D5 格式检测正则表达式
    /** 前缀模式: "摘要："、"简介："等 */
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^(简介|摘要|描述|总结|概述)[：:]\\s*");

    /** 引号包裹模式: "...", '...', 「...」等 */
    private static final Pattern QUOTE_PATTERN = Pattern.compile("^[\"'\\u300C\\u201C\\u2018`]+(.+?)[\"'\\u300D\\u201D\\u2019`]+$", Pattern.DOTALL);

    /** Markdown语法模式: **粗体**, ##标题, 列表符号等 */
    private static final Pattern MARKDOWN_PATTERN = Pattern.compile("\\*\\*|__|##|^- |^> |^\\d+\\. ", Pattern.MULTILINE);

    /** 元信息说明模式: "以下是..."、"根据...生成"等 */
    private static final Pattern META_TALK_PATTERN = Pattern.compile("^(以下是|这里是|为您生成|根据.+生成)");

    /**
     * 校验AI生成的输出是否符合质量标准
     *
     * @param rawOutput AI原始输出文本
     * @param finishReason 生成完成原因 ("stop" | "length" | ...)
     * @return 校验结果，包含有效性、失败原因和净化后的文本
     */
    public ValidationResult validate(String rawOutput, String finishReason) {
        // 1. 空值守卫
        if (rawOutput == null || rawOutput.isBlank()) {
            return ValidationResult.fail("EMPTY", "");
        }

        // 2. 净化美观性问题（非阻塞）
        String sanitized = rawOutput;

        // 2a. 去除前缀（但记录其存在）
        Matcher prefixMatcher = PREFIX_PATTERN.matcher(sanitized);
        boolean hadPrefix = prefixMatcher.find();
        if (hadPrefix) {
            sanitized = prefixMatcher.replaceFirst("");
        }

        // 2b. 去除引号包裹（但记录其存在）
        Matcher quoteMatcher = QUOTE_PATTERN.matcher(sanitized);
        boolean hadQuotes = quoteMatcher.matches();
        if (hadQuotes) {
            sanitized = quoteMatcher.group(1);
        }

        // 2c. 去除首尾空白
        sanitized = sanitized.strip();

        // 如果净化后为空，直接失败
        if (sanitized.isEmpty()) {
            return ValidationResult.fail("EMPTY", "");
        }

        // 3. 检查结构性问题（阻塞 - 触发重试）

        // 3a. 检查Markdown语法
        if (MARKDOWN_PATTERN.matcher(sanitized).find()) {
            return ValidationResult.fail("HAS_MARKDOWN", sanitized);
        }

        // 3b. 检查元信息说明
        if (META_TALK_PATTERN.matcher(sanitized).find()) {
            return ValidationResult.fail("HAS_META_TALK", sanitized);
        }

        // 4. 检查finish_reason
        if ("length".equals(finishReason)) {
            return ValidationResult.fail("TRUNCATED", sanitized);
        }

        // 5. 检查长度（使用codePointCount正确处理中文字符）
        int charCount = sanitized.codePointCount(0, sanitized.length());
        if (charCount < ARTICLE_SUMMARY_MIN_CHARS) {
            return ValidationResult.fail("TOO_SHORT", sanitized);
        }
        if (charCount > ARTICLE_SUMMARY_MAX_CHARS) {
            return ValidationResult.fail("TOO_LONG", sanitized);
        }

        // 6. 美观性问题判定（前缀、引号存在则失败，但不触发重试）
        if (hadPrefix) {
            return ValidationResult.fail("HAS_PREFIX", sanitized);
        }
        if (hadQuotes) {
            return ValidationResult.fail("HAS_QUOTES", sanitized);
        }

        // 所有检查通过
        return ValidationResult.pass(sanitized);
    }

    /**
     * 校验结果记录
     *
     * @param valid 是否通过校验
     * @param reason 失败原因（valid=true时为null）
     *               可能的值: "TOO_SHORT" | "TOO_LONG" | "TRUNCATED" |
     *                        "HAS_PREFIX" | "HAS_QUOTES" | "HAS_MARKDOWN" |
     *                        "HAS_META_TALK" | "EMPTY"
     * @param sanitized 净化后的输出文本（移除前缀、引号、空白后的内容）
     */
    public record ValidationResult(
        boolean valid,
        String reason,
        String sanitized
    ) {
        /**
         * 创建校验通过的结果
         */
        public static ValidationResult pass(String sanitized) {
            return new ValidationResult(true, null, sanitized);
        }

        /**
         * 创建校验失败的结果
         */
        public static ValidationResult fail(String reason, String sanitized) {
            return new ValidationResult(false, reason, sanitized);
        }
    }
}

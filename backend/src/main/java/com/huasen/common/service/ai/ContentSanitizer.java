package com.huasen.common.service.ai;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 内容清洗工具 - 为 AI 提示词准备干净的纯文本输入
 *
 * 职责：
 * - 移除 HTML 标签
 * - 转换 Markdown 为纯文本
 * - 解码 HTML 实体
 * - 规范化空白字符
 * - 截断超长内容（head + tail 策略）
 *
 * 使用场景：
 * - 文章摘要生成：将富文本正文转为纯文本送入 AI
 * - URL 描述生成：清洗抓取的网页内容
 *
 * 模式（类比 ParamUtil）：
 * - 无状态工具类，Spring 管理便于潜在的日志记录
 */
@Component
public class ContentSanitizer {

    private static final Logger logger = LoggerFactory.getLogger(ContentSanitizer.class);

    // Content length limits (per 12-RESEARCH §4.5)
    private static final int MAX_PROMPT_CHARS = 6000;  // Hard cap for AI input
    private static final int HEAD_CHARS = 4000;         // Take first 4000 chars
    private static final int TAIL_CHARS = 2000;         // Take last 2000 chars

    // Commonmark parser/renderer (thread-safe, reusable instances)
    private final Parser markdownParser = Parser.builder().build();
    private final TextContentRenderer textRenderer = TextContentRenderer.builder().build();

    /**
     * 清洗并截断内容，准备用于 AI 提示词
     *
     * 处理流程：
     * 1. 空值保护
     * 2. 移除 HTML 标签
     * 3. 解码 HTML 实体
     * 4. Markdown → 纯文本
     * 5. 规范化空白字符
     * 6. 截断（如需要）
     *
     * @param rawContent 原始内容（可能包含 HTML/Markdown）
     * @return 清洗后的纯文本，长度 ≤ MAX_PROMPT_CHARS
     */
    public String sanitizeForPrompt(String rawContent) {
        // 1. Null/blank guard
        if (rawContent == null || rawContent.isBlank()) {
            return "";
        }

        // 2. Strip HTML tags using regex (project pattern per 12-PATTERNS.md line 161)
        String withoutHtml = rawContent.replaceAll("<[^>]+>", "");

        // 3. Decode common HTML entities
        String decoded = withoutHtml
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");

        // 4. Strip Markdown to plaintext using commonmark library
        Node document = markdownParser.parse(decoded);
        String plaintext = textRenderer.render(document);

        // 5. Normalize whitespace: collapse multiple spaces/newlines/tabs to single space
        String normalized = plaintext.replaceAll("\\s+", " ").strip();

        // 6. Truncate if needed (head + tail strategy)
        if (normalized.length() <= MAX_PROMPT_CHARS) {
            return normalized;
        }

        // Take head (first 4000 chars) + separator + tail (last 2000 chars)
        String head = normalized.substring(0, HEAD_CHARS);
        String tail = normalized.substring(normalized.length() - TAIL_CHARS);
        String truncated = head + " [...] " + tail;

        logger.info("Content truncated from {} to {} chars for AI prompt",
                normalized.length(), truncated.length());

        return truncated;
    }
}

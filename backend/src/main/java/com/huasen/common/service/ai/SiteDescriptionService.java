package com.huasen.common.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * 网站描述生成服务
 *
 * 职责：基于网站 title / meta description，调用 AI 生成 6-15 字的极短网站介绍。
 *
 * 非阻塞契约（绝不抛异常，永远返回有效字符串）：
 * - title 与 metaDescription 都为空/空白 → 直接返回 ""
 * - AI 超时 / 报错 / API key 未配置 / 校验失败 → 返回 ""
 * - 任何异常整体捕获，记 warn 日志后返回 ""
 *
 * 轻量化（区别于 ArticleSummaryService）：
 * - 无重试、无 Redis 缓存、无遥测日志（AiGenerationLog）、无 ContentSanitizer
 * - 内联长度/格式净化，保持单文件可读
 *
 * @see com.huasen.common.service.ai.ArticleSummaryService 非阻塞编排参考范式
 */
@Service
public class SiteDescriptionService {

    private static final Logger logger = LoggerFactory.getLogger(SiteDescriptionService.class);

    /** 极短介绍最大字符数（codePoint 计数） */
    private static final int MAX_CHARS = 15;

    /** 前缀模式: "描述："、"简介："等 */
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^(简介|描述|介绍|总结|概述)[：:]\\s*");

    /** 引号包裹模式: "...", '...', 「...」等 */
    private static final Pattern QUOTE_PATTERN =
        Pattern.compile("^[\"'\\u300C\\u201C\\u2018`]+(.+?)[\"'\\u300D\\u201D\\u2019`]+$", Pattern.DOTALL);

    /** 句末标点（需去除） */
    private static final Pattern TRAILING_PUNCT_PATTERN =
        Pattern.compile("[。！？\\.!?,，；;、]+$");

    @Autowired
    private BailianClient bailianClient;

    /**
     * 生成网站极短介绍。
     *
     * @param title 网站标题（可能为空）
     * @param metaDescription 网站 meta 描述（可能为空）
     * @return 6-15 字的极短中文介绍，或 ""（输入空 / 任何失败）
     */
    public String generate(String title, String metaDescription) {
        boolean titleBlank = (title == null || title.isBlank());
        boolean metaBlank = (metaDescription == null || metaDescription.isBlank());
        if (titleBlank && metaBlank) {
            // 抓不到 title / meta → 直接返回空（非阻塞兜底）
            return "";
        }

        try {
            String systemPrompt = PromptTemplates.SITE_DESCRIPTION_SYSTEM_V1;
            String userPrompt = PromptTemplates.siteDescriptionUser(title, metaDescription);

            BailianClient.AiResult result = bailianClient.call(systemPrompt, userPrompt);
            String description = sanitize(result.content());

            if (description.isEmpty()) {
                logger.warn("Site description empty after sanitize, title={}", title);
                return "";
            }
            return description;
        } catch (Exception e) {
            // 超时 / 报错 / key 未配置 / 任何异常 → 静默降级为空（绝不抛出）
            logger.warn("Site description generation failed (non-blocking), title={}: {}",
                        title, e.getMessage());
            return "";
        }
    }

    /**
     * 从网址生成极短介绍：抓取首页 HTML → 提取 title/meta → 调 AI 生成。
     *
     * <p>供「插件 quick-add」复用「新增网链」同一套 AI 能力（插件不传 title/meta，
     * 故原料需服务端自行抓取）。整链路非阻塞：抓取/提取/AI 任一环节失败均返回 ""，
     * 绝不抛异常——调用方可安全地夹在 @Transactional 方法内而不触发回滚。
     *
     * @param url 目标网址（http/https）
     * @return 6-15 字极短介绍，或 ""（输入空 / 抓取失败 / 任何失败）
     */
    public String generateFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            return generateFromHtml(response.body());
        } catch (Exception e) {
            // 网址非法 / 抓取超时 / 任何异常 → 静默降级为空（非阻塞兜底）
            logger.warn("Site description generateFromUrl failed (non-blocking), url={}: {}",
                        url, e.getMessage());
            return "";
        }
    }

    /**
     * 从已抓取的首页 HTML 生成极短介绍：提取 title/meta → 调 AI 生成。
     *
     * <p>供「新增网链」的 {@code fetchFavicons} 复用——它抓 HTML 取图标时顺带生成描述，
     * 零额外网络请求。非阻塞：任何失败返回 ""。
     *
     * @param html 首页 HTML（可能为 null / 空）
     * @return 6-15 字极短介绍，或 ""
     */
    public String generateFromHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String title = extractTitle(html);
        String metaDescription = extractMetaDescription(html);
        return generate(title, metaDescription);
    }

    /**
     * 从首页 HTML 中提取 &lt;title&gt; 文本（首个，CASE_INSENSITIVE + DOTALL）。
     *
     * @param html 首页 HTML
     * @return title 文本（已 strip），无则 ""
     */
    private String extractTitle(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        java.util.regex.Matcher m = Pattern.compile(
                "<title[^>]*>(.*?)</title>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(html);
        if (m.find()) {
            return m.group(1).strip();
        }
        return "";
    }

    /**
     * 从首页 HTML 中提取 &lt;meta name="description" content="..."&gt;。
     * 属性顺序可能为 content 在前 name 在后，两种顺序都尝试。
     *
     * @param html 首页 HTML
     * @return meta description 内容（已 strip），无则 ""
     */
    private String extractMetaDescription(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        // name 在前，content 在后
        java.util.regex.Matcher m1 = Pattern.compile(
                "<meta[^>]*name=[\"']description[\"'][^>]*content=[\"']([^\"']*)[\"']",
                Pattern.CASE_INSENSITIVE)
                .matcher(html);
        if (m1.find()) {
            return m1.group(1).strip();
        }
        // content 在前，name 在后
        java.util.regex.Matcher m2 = Pattern.compile(
                "<meta[^>]*content=[\"']([^\"']*)[\"'][^>]*name=[\"']description[\"']",
                Pattern.CASE_INSENSITIVE)
                .matcher(html);
        if (m2.find()) {
            return m2.group(1).strip();
        }
        return "";
    }

    /**
     * 净化 AI 输出：去前缀、去引号、去首尾空白、去句末标点，超长截断到 ≤15 字。
     *
     * @param raw AI 原始输出
     * @return 净化后的极短介绍（可能为空字符串）
     */
    private String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.strip();

        // 去前缀（如"描述："）
        s = PREFIX_PATTERN.matcher(s).replaceFirst("");

        // 去引号包裹
        java.util.regex.Matcher quoteMatcher = QUOTE_PATTERN.matcher(s);
        if (quoteMatcher.matches()) {
            s = quoteMatcher.group(1);
        }

        // 去除换行（极短介绍应为单行）
        s = s.replaceAll("[\\r\\n]+", " ").strip();

        // 去句末标点
        s = TRAILING_PUNCT_PATTERN.matcher(s).replaceFirst("");

        s = s.strip();
        if (s.isEmpty()) {
            return "";
        }

        // 超长截断到 ≤15 字（codePoint 计数，避免中文按字符截断乱码）
        int codePoints = s.codePointCount(0, s.length());
        if (codePoints > MAX_CHARS) {
            int endIdx = s.offsetByCodePoints(0, MAX_CHARS);
            s = s.substring(0, endIdx).strip();
        }

        return s;
    }
}

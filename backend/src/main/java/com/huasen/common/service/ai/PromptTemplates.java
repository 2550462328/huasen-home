package com.huasen.common.service.ai;

/**
 * AI提示词模板 - 集中管理所有AI生成任务的提示词
 *
 * 提示词版本化策略：
 * - 每个提示词常量带版本后缀（如 _V1）
 * - 修改提示词时递增版本号（V1 -> V2）
 * - 版本号同步更新到 PROMPT_VERSION 常量
 * - 版本号嵌入Redis缓存键，实现自动失效
 *
 * Prompt Engineering 原则（针对Qwen-Plus）：
 * - System vs User分离：角色/约束放system，内容放user
 * - 明确长度约束：使用100-200字风格（Qwen遵守字符数）
 * - 负面约束：显式禁止前缀、引号、Markdown等
 * - Few-shot可选：根据质量需要内联1-2个示例
 * - max_tokens必设：避免无界生成
 */
public final class PromptTemplates {

    private PromptTemplates() {
        // 工具类，禁止实例化
    }

    // ==================== 文章摘要生成 ====================

    /**
     * 文章摘要提示词版本
     * 修改提示词时递增此版本号，触发缓存自动失效
     */
    public static final String ARTICLE_SUMMARY_PROMPT_VERSION = "v9";

    /**
     * 文章摘要 - System Prompt (角色定义 + 约束条件)
     *
     * 设计说明：
     * - 角色定位：中文文章摘要编辑
     * - 长度要求：180-260字（实际校验 180-320）
     * - 内容原则：忠实原文、不引入外部信息、不发表评价
     * - 质量要求：抓住核心论点与关键结论，避免复述背景
     * - 格式约束：只输出摘要文本，禁止前缀、引号、Markdown
     *
     * 版本历史：
     * - v1~v8: 见 git
     * - v9 (2026-06-11): 还原最初的简洁版,长度调到 180-260
     */
    public static final String ARTICLE_SUMMARY_SYSTEM_V1 =
        "你是一名中文文章摘要编辑。" +
        "基于给定正文生成摘要，长度严格控制在 180-260 字之间，务必不超过 280 字，忠实于原文、不引入外部信息、不发表评价。" +
        "用三到四句话概括文章的核心论点与关键结论，可保留必要的背景与方法描述，但不要逐点罗列。" +
        "只输出摘要文本本身，不要加前缀（如摘要冒号），不要用引号包裹，不要使用Markdown格式（加粗、标题、列表符号等）。";

    /**
     * 文章摘要 - User Prompt 模板
     *
     * @param articleContent 已净化的文章正文（HTML/Markdown已剥离，截断至~6000字符）
     * @return 格式化的用户提示词
     */
    public static String articleSummaryUser(String articleContent) {
        return "请为以下文章生成摘要：\n\n" + articleContent;
    }

    // ==================== Few-shot示例（可选） ====================

    /**
     * Few-shot示例 - 文章摘要
     *
     * 当前未启用。如果生成质量不足，可在 ARTICLE_SUMMARY_SYSTEM_V1 末尾追加示例。
     * 注意：Few-shot会增加token消耗，仅在必要时启用。
     */
    // 预留，当前版本未使用

    // ==================== 未来扩展预留 ====================

    // ==================== 网站描述生成 ====================

    /**
     * 网站描述提示词版本
     * 修改提示词时递增此版本号
     */
    public static final String SITE_DESCRIPTION_PROMPT_VERSION = "v1";

    /**
     * 网站描述 - System Prompt (角色定义 + 约束条件)
     *
     * 设计说明：
     * - 角色定位：网站简介撰写者
     * - 长度要求：6-15 个中文字符的极短介绍
     * - 内容原则：只说网站是做什么的（如"前端开发者技术社区"）
     * - 格式约束：禁止营销词、句末标点、引号、Markdown、前缀
     */
    public static final String SITE_DESCRIPTION_SYSTEM_V1 =
        "你是一名网站简介撰写者。" +
        "根据给定的网站标题与网站描述，用一句 6-15 个汉字的极短中文介绍说明这个网站是做什么的。" +
        "示例风格：前端开发者技术社区、在线流程图协作工具、开源代码托管平台。" +
        "只输出这句极短介绍本身，不要加任何前缀（如描述冒号），不要用引号包裹，" +
        "不要使用Markdown格式，不要使用营销词（如最强、领先、一站式），不要在句末添加标点符号。";

    /**
     * 网站描述 - User Prompt 模板
     *
     * @param title 网站标题（可能为空）
     * @param metaDescription 网站 meta 描述（可能为空）
     * @return 格式化的用户提示词
     */
    public static String siteDescriptionUser(String title, String metaDescription) {
        StringBuilder sb = new StringBuilder("请为以下网站撰写极短介绍：\n\n");
        sb.append("网站标题：").append(title == null ? "" : title.strip()).append("\n");
        sb.append("网站描述：").append(metaDescription == null ? "" : metaDescription.strip());
        return sb.toString();
    }

    /**
     * 预留：标签推荐
     * Phase 12 暂不实现，预留接口供 Phase 13+ 使用
     */
    // public static final String TAG_RECOMMENDATION_PROMPT_VERSION = "v1";
    // public static final String TAG_RECOMMENDATION_SYSTEM_V1 = "...";
}

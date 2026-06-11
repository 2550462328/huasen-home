package com.huasen.common.constant;

/**
 * Redis键常量
 * 与Node.js后端config.js中的POOL_*常量保持一致
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {
        // 防止实例化
    }

    /** 令牌池 - 存储用户JWT token */
    public static final String POOL_TOKEN = "POOL_TOKEN";

    /** 黑名单资源池 - 存储被封禁的IP */
    public static final String POOL_BLACKLIST = "POOL_BLACKLIST";

    /** 用户信息池 - 存储访问记录 */
    public static final String POOL_ACCESS = "POOL_ACCESS";

    /** 邮箱验证码池 - 存储邮箱验证码 */
    public static final String MAIL_CODE_POOL = "MAIL_CODE_POOL";

    /**
     * AI生成结果缓存 key 前缀 (Phase 12)
     *
     * 缓存键格式: ai:{feature}:{prompt_version}:{content_hash}
     * 示例: ai:article:v1:a3f5e8d9c2b1...
     *
     * 组成部分:
     * - feature: 功能类型 (article=文章摘要, site=网站描述等)
     * - prompt_version: 提示词版本 (来自 PromptTemplates.ARTICLE_SUMMARY_PROMPT_VERSION)
     * - content_hash: 内容SHA-256哈希 (用于去重)
     *
     * 版本化策略:
     * - 修改提示词时递增版本号 (v1 -> v2)
     * - 旧版本缓存自动失效 (新键不匹配旧键)
     * - 避免手动清理缓存
     */
    public static final String AI_CACHE_PREFIX = "ai:";

    /**
     * PV 计数器 key 前缀 (Phase 13)
     *
     * 完整键格式: PV:{yyyyMMdd}:{user|manage|other}
     * 示例: PV:20260610:user
     *
     * 每个 bucket 一个 Redis 计数器，由 PV 过滤器按访问来源 INCR，
     * 夜间快照任务读取后写入 daily_metric_snapshot。
     */
    public static final String PV_KEY_PREFIX = "PV:";

    /**
     * UV 独立访客 Set key 前缀 (Phase 13)
     *
     * 完整键格式: UV:{yyyyMMdd}
     * 示例: UV:20260610
     *
     * 当日访客标识 (如 IP/指纹) 存入 Redis Set，SCARD 得到当日 UV 基数。
     */
    public static final String UV_KEY_PREFIX = "UV:";

    /**
     * 数据表盘缓存 key 前缀 (Phase 13)
     *
     * 完整键格式: CACHE:DASHBOARD:{name}
     * 示例: CACHE:DASHBOARD:overview
     *
     * 表盘聚合查询结果缓存，按端点名区分，避免重复重计算。
     */
    public static final String CACHE_DASHBOARD_PREFIX = "CACHE:DASHBOARD:";
}

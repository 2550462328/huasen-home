package com.huasen.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huasen.blog.sharon.entity.BlogPost;
import com.huasen.blog.sharon.repository.BlogPostRepository;
import com.huasen.blog.tiny.entity.TinyBlogPost;
import com.huasen.blog.tiny.repository.TinyBlogPostRepository;
import com.huasen.common.constant.RedisKeyConstants;
import com.huasen.common.entity.DailyMetricSnapshot;
import com.huasen.common.repository.*;
import com.huasen.common.service.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 数据表盘聚合服务 (Phase 13, Plan 04)
 *
 * 提供三类表盘数据聚合:
 * 1. getOverview() - 站点/栏目/文章/用户总数
 * 2. getPvTrend() - 时序 PV/UV 趋势 (按天)
 * 3. getArticleRank(limit) - 文章访问量 TopN (sharon+tiny 合并)
 *
 * 所有方法均实现 Redis 读穿缓存 (5分钟 TTL),
 * 避免多管理员同时打开表盘或单管理员刷新时重复执行昂贵的聚合查询。
 */
@Service
public class DashboardService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);

    private static final long CACHE_TTL_MINUTES = 5L;

    /** 月度聚合桶格式: yyyy-MM */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private ColumnRepository columnRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DailyMetricSnapshotRepository snapshotRepository;

    @Autowired
    private BlogPostRepository blogPostRepository;

    @Autowired
    private TinyBlogPostRepository tinyBlogPostRepository;

    @Autowired
    private RedisService redisService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取系统概览计数 (站点/栏目/文章/用户)
     *
     * @return {siteCount, columnCount, articleCount, userCount}
     */
    public Map<String, Object> getOverview() {
        String cacheKey = RedisKeyConstants.CACHE_DASHBOARD_PREFIX + "overview";

        // Check cache
        try {
            String cached = redisService.get(cacheKey);
            if (cached != null) {
                logger.debug("Cache hit for {}", cacheKey);
                return objectMapper.readValue(cached, Map.class);
            }
        } catch (Exception e) {
            logger.warn("Cache read failed for {}: {}", cacheKey, e.getMessage());
        }

        // Cache miss - compute
        logger.debug("Cache miss for {}, computing...", cacheKey);

        Map<String, Object> result = new HashMap<>();
        result.put("siteCount", siteRepository.count());
        result.put("columnCount", columnRepository.count());
        result.put("articleCount", articleRepository.count());
        result.put("userCount", userRepository.count());

        // Cache result
        try {
            String json = objectMapper.writeValueAsString(result);
            redisService.set(cacheKey, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            logger.warn("Cache write failed for {}: {}", cacheKey, e.getMessage());
        }

        return result;
    }

    /**
     * 获取 PV/UV 趋势 (按天, 兼容旧调用)
     *
     * @return 时序数据 (平行数组)
     */
    public Map<String, Object> getPvTrend() {
        return getPvTrend("day");
    }

    /**
     * 获取 PV/UV 趋势, 支持按日/按月聚合
     *
     * 从 DailyMetricSnapshot 表读取历史快照,
     * 透视为前端 ECharts 兼容的平行数组格式:
     * {
     *   dates: ["2026-06-01", ...] (day) 或 ["2026-06", ...] (month),
     *   user: [120, ...],
     *   manage: [45, ...],
     *   other: [30, ...],
     *   uv: [150, ...]
     * }
     *
     * 按月聚合时, 同月的每日快照按指标类型累加 (UV 同样累加日去重值,
     * 作为"月活跃访问量"近似 — 单表快照无法跨日精确去重)。
     *
     * @param granularity "day" (按日) 或 "month" (按月), 其他值按 day 处理
     * @return 时序数据 (平行数组)
     */
    public Map<String, Object> getPvTrend(String granularity) {
        boolean byMonth = "month".equalsIgnoreCase(granularity);
        String scope = byMonth ? "month" : "day";
        String cacheKey = RedisKeyConstants.CACHE_DASHBOARD_PREFIX + "pvTrend:" + scope;

        // Check cache
        try {
            String cached = redisService.get(cacheKey);
            if (cached != null) {
                logger.debug("Cache hit for {}", cacheKey);
                return objectMapper.readValue(cached, Map.class);
            }
        } catch (Exception e) {
            logger.warn("Cache read failed for {}: {}", cacheKey, e.getMessage());
        }

        // Cache miss - compute
        logger.debug("Cache miss for {}, computing...", cacheKey);

        List<DailyMetricSnapshot> snapshots = snapshotRepository.findAllByOrderByMetricDateAsc();

        // Group by bucket (day=yyyy-MM-dd, month=yyyy-MM), 累加同桶内指标
        Map<String, Map<String, Long>> byBucket = new LinkedHashMap<>();
        for (DailyMetricSnapshot snapshot : snapshots) {
            String bucket = byMonth
                    ? snapshot.getMetricDate().format(MONTH_FORMATTER)
                    : snapshot.getMetricDate().toString();
            byBucket.computeIfAbsent(bucket, k -> new HashMap<>())
                    .merge(snapshot.getMetricType(), snapshot.getMetricValue(), Long::sum);
        }

        // Pivot into parallel arrays
        List<String> dates = new ArrayList<>();
        List<Long> user = new ArrayList<>();
        List<Long> manage = new ArrayList<>();
        List<Long> other = new ArrayList<>();
        List<Long> uv = new ArrayList<>();

        List<String> sortedBuckets = byBucket.keySet().stream()
                                              .sorted()
                                              .collect(Collectors.toList());

        for (String bucket : sortedBuckets) {
            Map<String, Long> metrics = byBucket.get(bucket);
            dates.add(bucket);
            user.add(metrics.getOrDefault("PV_USER", 0L));
            manage.add(metrics.getOrDefault("PV_MANAGE", 0L));
            other.add(metrics.getOrDefault("PV_OTHER", 0L));
            uv.add(metrics.getOrDefault("UV", 0L));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("user", user);
        result.put("manage", manage);
        result.put("other", other);
        result.put("uv", uv);
        result.put("granularity", scope);

        // Cache result
        try {
            String json = objectMapper.writeValueAsString(result);
            redisService.set(cacheKey, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            logger.warn("Cache write failed for {}: {}", cacheKey, e.getMessage());
        }

        return result;
    }

    /**
     * 获取文章访问量 TopN 排行
     *
     * 合并 blog-sharon (postViews) 和 tiny-blog (visitCount),
     * 归一化为统一 DTO 格式后按访问量降序排列,取前 N 条。
     *
     * @param limit TopN 数量 (例如 10)
     * @return List<{title, views, source, id}>
     */
    public List<Map<String, Object>> getArticleRank(int limit) {
        String cacheKey = RedisKeyConstants.CACHE_DASHBOARD_PREFIX + "articleRank";

        // Check cache
        try {
            String cached = redisService.get(cacheKey);
            if (cached != null) {
                logger.debug("Cache hit for {}", cacheKey);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> result = objectMapper.readValue(cached, List.class);
                return result;
            }
        } catch (Exception e) {
            logger.warn("Cache read failed for {}: {}", cacheKey, e.getMessage());
        }

        // Cache miss - compute
        logger.debug("Cache miss for {}, computing...", cacheKey);

        List<Map<String, Object>> rank = new ArrayList<>();

        // Fetch sharon articles
        List<BlogPost> sharonPosts = blogPostRepository.findTop10ByOrderByPostViewsDesc();
        for (BlogPost post : sharonPosts) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("title", post.getPostTitle());
            entry.put("views", post.getPostViews() == null ? 0L : post.getPostViews());
            entry.put("source", "sharon");
            entry.put("id", post.getPostId());
            rank.add(entry);
        }

        // Fetch tiny articles
        List<TinyBlogPost> tinyPosts = tinyBlogPostRepository.findTop10ByOrderByVisitCountDesc();
        for (TinyBlogPost post : tinyPosts) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("title", post.getTitle());
            entry.put("views", post.getVisitCount() == null ? 0L : post.getVisitCount().longValue());
            entry.put("source", "tiny");
            entry.put("id", post.getId());
            rank.add(entry);
        }

        // Sort by views DESC
        rank.sort((a, b) -> Long.compare((Long) b.get("views"), (Long) a.get("views")));

        // Limit to TopN
        List<Map<String, Object>> topN = rank.stream().limit(limit).collect(Collectors.toList());

        // Cache result
        try {
            String json = objectMapper.writeValueAsString(topN);
            redisService.set(cacheKey, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            logger.warn("Cache write failed for {}: {}", cacheKey, e.getMessage());
        }

        return topN;
    }
}

package com.huasen.common.filter;

import com.huasen.common.constant.RedisKeyConstants;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * PV/UV 采集过滤器 (Phase 13)
 *
 * 对接原 Node.js 的 user/manage/other 访问量分桶逻辑, 但只做内存级 Redis 操作:
 * - 按请求头 {@code dot} 分桶 (user|manage|缺省→other), 对 {@code PV:{yyyyMMdd}:{bucket}} 做 INCR
 * - 把客户端 IP 加入当日 {@code UV:{yyyyMMdd}} Set, 按 IP 天然去重
 *
 * 关键约束 (D-01/D-12 + RESEARCH Pattern 1):
 * - 请求路径零 DB 写入 — 仅 Redis INCR/SADD (毫秒级)
 * - 永不阻断 — 任何分支都放行请求链
 * - Redis 故障不影响页面加载 — 全部 Redis 操作包 try/catch
 *
 * 注册顺序 order=4 (FilterConfig), 位于 JwtAuthFilter(2)/BlacklistFilter(3) 之后:
 * 此时 dot/认证上下文已就绪, 黑名单 IP 已被拒绝, 不会被计入 UV。
 */
@Component
public class PvCollectFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(PvCollectFilter.class);

    /** 当日 key 时区 — 固定 Asia/Shanghai, 避免服务器 UTC 导致跨日漂移 (RESEARCH Pitfall 4) */
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private StringRedisTemplate redis;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest http = (HttpServletRequest) req;

        try {
            String uri = http.getRequestURI();
            // 跳过预检请求与已上 CDN 的静态资源
            if (!"OPTIONS".equalsIgnoreCase(http.getMethod())
                    && (uri == null || !uri.startsWith("/huasen-store/"))) {

                String dot = http.getHeader("dot");
                String bucket = "user".equals(dot) ? "user"
                        : "manage".equals(dot) ? "manage"
                        : "other";

                String day = LocalDate.now(ZONE_SHANGHAI)
                        .format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd

                redis.opsForValue().increment(RedisKeyConstants.PV_KEY_PREFIX + day + ":" + bucket);
                redis.opsForSet().add(RedisKeyConstants.UV_KEY_PREFIX + day, resolveIp(http));
            }
        } catch (Exception e) {
            // 采集失败绝不影响请求 — 记录后放行
            log.warn("PV采集跳过: {}", e.getMessage());
        }

        // 永远放行
        chain.doFilter(req, res);
    }

    /**
     * 解析客户端真实 IP (Nginx 反向代理场景)
     * 优先级: X-Forwarded-For → X-Real-IP → remoteAddr
     * 复用 BlacklistFilter.getClientIp 的解析口径。
     */
    private String resolveIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}

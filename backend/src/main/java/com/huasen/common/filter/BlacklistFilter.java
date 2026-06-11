package com.huasen.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huasen.common.constant.RedisKeyConstants;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 黑名单过滤器
 * 对应Node.js的handleBlackList中间件
 * 检查客户端IP是否在Redis黑名单中，如果在则拒绝请求
 */
@Component
public class BlacklistFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(BlacklistFilter.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // 跳过静态资源请求
        String requestUri = httpRequest.getRequestURI();
        if (requestUri.startsWith("/huasen-store/")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(httpRequest);

        try {
            // Node.js中黑名单存储为Redis Hash（对象），检查IP是否为其中的key
            Set<Object> blacklistKeys = redisTemplate.opsForHash()
                    .keys(RedisKeyConstants.POOL_BLACKLIST);

            if (blacklistKeys != null && !blacklistKeys.isEmpty()) {
                // 与Node.js逻辑一致：检查IP是否包含在黑名单的任一条目中
                boolean blocked = blacklistKeys.stream()
                        .anyMatch(item -> clientIp.contains(item.toString()));

                if (blocked) {
                    log.warn("黑名单拦截, IP={}", clientIp);
                    sendBlockedResponse(response);
                    return;
                }
            }
        } catch (Exception e) {
            // Redis异常时不阻断请求，记录错误后放行
            log.error("黑名单检查异常: {}", e.getMessage(), e);
        }

        chain.doFilter(request, response);
    }

    /**
     * 获取客户端真实IP
     * 优先从X-Forwarded-For头获取（Nginx反向代理场景）
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For可能包含多个IP，取第一个
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * 返回黑名单拦截响应
     * 对应Node.js: global.huasen.responseData(res, {}, 'ERROR', '黑名单拦截', false)
     */
    private void sendBlockedResponse(ServletResponse response) throws IOException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setStatus(400);
        httpResponse.setContentType("application/json;charset=UTF-8");

        Map<String, Object> body = new HashMap<>();
        body.put("code", 400);
        body.put("msg", "黑名单拦截");
        body.put("data", new HashMap<>());

        String json = objectMapper.writeValueAsString(body);
        httpResponse.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
        httpResponse.getOutputStream().flush();
    }
}

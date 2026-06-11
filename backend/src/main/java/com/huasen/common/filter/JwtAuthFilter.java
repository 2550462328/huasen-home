package com.huasen.common.filter;

import com.huasen.common.util.JwtUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * JWT认证过滤器
 * 对应Node.js的handleJWT中间件
 *
 * 关键行为：
 * - 从请求头"token"字段读取token（非标准Authorization头，前端兼容要求）
 * - 验证成功：将key和code设置到请求属性中
 * - 验证失败：设置默认值{key: "", code: 0}
 * - 永远不阻断请求（non-blocking filter）— 权限检查在Controller层完成
 */
@Component
public class JwtAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    /** 请求属性名 - JWT解析出的用户标识 */
    public static final String ATTR_JWT_KEY = "huasenJWT_key";

    /** 请求属性名 - JWT解析出的权限码 */
    public static final String ATTR_JWT_CODE = "huasenJWT_code";

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // 从"token"请求头读取（关键：前端发送的是名为"token"的header）
        String token = httpRequest.getHeader("token");

        Map<String, Object> result = jwtUtil.verifyToken(token);

        if (result != null) {
            // 验证成功，设置用户信息到请求属性
            httpRequest.setAttribute(ATTR_JWT_KEY, result.get("key"));
            httpRequest.setAttribute(ATTR_JWT_CODE, result.get("code"));
            log.debug("JWT验证成功, key={}", result.get("key"));
        } else {
            // 验证失败或无token，设置默认值（游客）
            httpRequest.setAttribute(ATTR_JWT_KEY, "");
            httpRequest.setAttribute(ATTR_JWT_CODE, 0);
        }

        // 永远放行，不阻断请求
        chain.doFilter(request, response);
    }
}

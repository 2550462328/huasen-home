package com.huasen.common.filter;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 过滤器注册配置
 * 按顺序注册四个过滤器，对应Node.js中间件链：
 * 1. RequestParamsFilter (RSA参数解密)
 * 2. JwtAuthFilter (JWT认证)
 * 3. BlacklistFilter (黑名单拦截)
 * 4. PvCollectFilter (PV/UV访问量采集，非阻断)
 */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<RequestParamsFilter> requestParamsFilterRegistration(
            RequestParamsFilter filter) {
        FilterRegistrationBean<RequestParamsFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        registration.setName("requestParamsFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(
            JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(2);
        registration.setName("jwtAuthFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<BlacklistFilter> blacklistFilterRegistration(
            BlacklistFilter filter) {
        FilterRegistrationBean<BlacklistFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(3);
        registration.setName("blacklistFilter");
        return registration;
    }

    /**
     * PV/UV 采集过滤器注册 (Phase 13)
     *
     * order=4 — 位于 JwtAuthFilter(2)/BlacklistFilter(3) 之后:
     * 此时 dot/认证上下文已就绪, 黑名单 IP 已被拒绝, 不会被计入 UV。
     * 该过滤器永不阻断, 仅做 Redis INCR/SADD 采集。
     */
    @Bean
    public FilterRegistrationBean<PvCollectFilter> pvCollectFilterRegistration(
            PvCollectFilter filter) {
        FilterRegistrationBean<PvCollectFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(4);
        registration.setName("pvCollectFilter");
        return registration;
    }

}

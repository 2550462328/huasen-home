package com.huasen.common.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PvCollectFilter 单元测试
 *
 * 覆盖需求:
 * - D-01/D-12: Filter 仅做 Redis INCR + SADD, 永不阻断, 永不写 DB
 * - D-02: 同一天内同一 IP 去重 (Redis Set 天然去重)
 * - Target 1: dot 请求头 → user/manage/other 分桶
 *
 * 策略: mock StringRedisTemplate, 用 MockHttpServletRequest 构造各类请求,
 * 断言 INCR/SADD 的 key 正确, 且 chain.doFilter 在所有分支(含 Redis 异常)都被调用。
 */
class PvCollectFilterTest {

    private PvCollectFilter filter;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private SetOperations<String, String> setOps;

    @Mock
    private FilterChain chain;

    private MockHttpServletResponse response;

    /** 与 Filter 内部一致的当日 key, 用于断言 */
    private String today;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new PvCollectFilter();
        ReflectionTestUtils.setField(filter, "redis", redis);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForSet()).thenReturn(setOps);

        today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.BASIC_ISO_DATE);

        response = new MockHttpServletResponse();
    }

    private MockHttpServletRequest apiRequest(String method, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod(method);
        req.setRequestURI(uri);
        req.setRemoteAddr("203.0.113.7");
        return req;
    }

    @Test
    void dotUser_incrementsUserBucketAndAddsUv() throws Exception {
        MockHttpServletRequest req = apiRequest("POST", "/api/site/list");
        req.addHeader("dot", "user");

        filter.doFilter(req, response, chain);

        verify(valueOps).increment("PV:" + today + ":user");
        verify(setOps).add("UV:" + today, "203.0.113.7");
        verify(chain).doFilter(req, response);
    }

    @Test
    void dotManage_incrementsManageBucket() throws Exception {
        MockHttpServletRequest req = apiRequest("POST", "/api/manage/overview");
        req.addHeader("dot", "manage");

        filter.doFilter(req, response, chain);

        verify(valueOps).increment("PV:" + today + ":manage");
        verify(chain).doFilter(req, response);
    }

    @Test
    void noDotHeader_incrementsOtherBucket() throws Exception {
        MockHttpServletRequest req = apiRequest("GET", "/api/blog/post");

        filter.doFilter(req, response, chain);

        verify(valueOps).increment("PV:" + today + ":other");
        verify(chain).doFilter(req, response);
    }

    @Test
    void optionsRequest_isSkippedButChainContinues() throws Exception {
        MockHttpServletRequest req = apiRequest("OPTIONS", "/api/site/list");
        req.addHeader("dot", "user");

        filter.doFilter(req, response, chain);

        verify(valueOps, never()).increment(anyString());
        verify(setOps, never()).add(anyString(), any());
        verify(chain).doFilter(req, response);
    }

    @Test
    void huasenStorePath_isSkippedButChainContinues() throws Exception {
        MockHttpServletRequest req = apiRequest("GET", "/huasen-store/article/1.png");
        req.addHeader("dot", "user");

        filter.doFilter(req, response, chain);

        verify(valueOps, never()).increment(anyString());
        verify(setOps, never()).add(anyString(), any());
        verify(chain).doFilter(req, response);
    }

    @Test
    void resolvesIpFromXForwardedFor() throws Exception {
        MockHttpServletRequest req = apiRequest("POST", "/api/site/list");
        req.addHeader("dot", "user");
        req.addHeader("X-Forwarded-For", "198.51.100.42, 10.0.0.1");

        filter.doFilter(req, response, chain);

        verify(setOps).add("UV:" + today, "198.51.100.42");
    }

    @Test
    void redisFailure_doesNotBlockRequest() throws Exception {
        MockHttpServletRequest req = apiRequest("POST", "/api/site/list");
        req.addHeader("dot", "user");
        when(valueOps.increment(anyString()))
                .thenThrow(new RuntimeException("redis down"));

        filter.doFilter(req, response, chain);

        // 请求必须放行, 即使 Redis 抛异常
        verify(chain).doFilter(req, response);
    }
}

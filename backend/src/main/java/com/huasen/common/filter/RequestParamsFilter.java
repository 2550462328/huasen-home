package com.huasen.common.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huasen.common.util.RsaUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 请求参数预处理过滤器
 * 对应Node.js的handleRequestParams中间件
 * 检测POST请求中secretMethod=rsa的情况，使用RSA私钥解密secretText
 */
@Component
public class RequestParamsFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestParamsFilter.class);

    @Autowired
    private RsaUtil rsaUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // 只处理POST请求且Content-Type为JSON
        if ("POST".equalsIgnoreCase(httpRequest.getMethod())
                && httpRequest.getContentType() != null
                && httpRequest.getContentType().contains("application/json")) {

            // 先包装请求以支持重复读取body
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(httpRequest);
            String body = cachedRequest.getCachedBody();

            if (body != null && !body.isBlank()) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(body);

                    // 检测是否需要RSA解密
                    if (jsonNode.has("secretMethod")
                            && "rsa".equals(jsonNode.get("secretMethod").asText())
                            && jsonNode.has("secretText")
                            && rsaUtil.isConfigured()) {

                        String secretText = jsonNode.get("secretText").asText();
                        String decrypted = rsaUtil.decryptLong(secretText);
                        log.debug("RSA参数解密成功");

                        // 用解密后的内容替换请求体
                        cachedRequest = new CachedBodyHttpServletRequest(
                                httpRequest, decrypted.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Exception e) {
                    log.error("请求参数预处理失败: {}", e.getMessage(), e);
                    // 解密失败时继续传递原始请求
                }
            }

            chain.doFilter(cachedRequest, response);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 可缓存请求体的HttpServletRequest包装器
     * 解决InputStream只能读取一次的问题
     */
    static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        public CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.cachedBody = body;
        }

        public String getCachedBody() {
            return new String(cachedBody, StandardCharsets.UTF_8);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return byteArrayInputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // 不需要异步支持
                }

                @Override
                public int read() {
                    return byteArrayInputStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(
                    new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return cachedBody.length;
        }

        @Override
        public long getContentLengthLong() {
            return cachedBody.length;
        }
    }
}

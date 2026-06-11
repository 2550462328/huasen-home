package com.huasen.common.util;

import com.huasen.common.constant.RedisKeyConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JWT工具类
 * 复制Node.js后端的JWT逻辑：
 * - 创建token并存入Redis（带TTL）
 * - 验证token合法性，检查Redis中是否存在
 * - 验证成功后续期（重置TTL）
 * - 同一用户新登录覆盖旧token（单设备登录）
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${huasen.jwt.secret:huasen-jwt-secret-key-2024-portal}")
    private String jwtSecret;

    @Value("${huasen.jwt.expire-seconds:604800}")
    private long expireSeconds;

    /**
     * 创建Token
     * 对应Node.js: JWT.createToken(key, payload)
     * 生成JWT并存入Redis，设置过期时间
     *
     * @param key  用户标识（通常是邮箱/ID）
     * @param code 权限码（0=游客, 1=普通用户, 2=编辑, 3=管理员）
     * @return 生成的token字符串
     */
    public String createToken(String key, Integer code) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("key", key);
        claims.put("code", code);

        String token = Jwts.builder()
                .claims(claims)
                .signWith(getSigningKey())
                .compact();

        // 存入Redis，key格式: POOL_TOKEN_<userKey>
        String redisKey = getTokenRedisKey(key);
        stringRedisTemplate.opsForValue().set(redisKey, token, expireSeconds, TimeUnit.SECONDS);

        log.debug("Token创建成功, key={}", key);
        return token;
    }

    /**
     * 验证Token
     * 对应Node.js: JWT.verifyToken(token)
     * 1. 校验JWT签名合法性
     * 2. 从Redis检查token是否存在（未过期）
     * 3. 验证成功后续期（重置TTL）
     *
     * @param token 待验证的token
     * @return 验证成功返回包含key和code的Map，失败返回null
     */
    public Map<String, Object> verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            // 1. 解析JWT，验证签名
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String key = claims.get("key", String.class);
            Integer code = claims.get("code", Integer.class);

            if (key == null) {
                return null;
            }

            // 2. 检查Redis中是否存在（是否已过期或被踢出）
            String redisKey = getTokenRedisKey(key);
            String storedToken = stringRedisTemplate.opsForValue().get(redisKey);

            if (storedToken == null) {
                // Token已过期或不存在
                log.debug("Token已过期, key={}", key);
                return null;
            }

            // 3. 验证成功，续期
            stringRedisTemplate.expire(redisKey, expireSeconds, TimeUnit.SECONDS);

            Map<String, Object> result = new HashMap<>();
            result.put("key", key);
            result.put("code", code);
            return result;

        } catch (Exception e) {
            log.debug("Token验证失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取JWT签名密钥
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 获取Token在Redis中的key
     * 格式: POOL_TOKEN_<userKey>
     */
    private String getTokenRedisKey(String key) {
        return RedisKeyConstants.POOL_TOKEN + "_" + key;
    }
}

package com.huasen.common.util;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES对称加解密工具
 * 复制Node.js后端的AES-128-CBC加解密逻辑
 * Node.js使用两个16位密钥：secrets[0]作为key，secrets[1]作为IV
 */
@Component
public class AesUtil {

    private static final Logger log = LoggerFactory.getLogger(AesUtil.class);

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    /** AES密钥（16字节），对应Node.js的SECRET_AES[0] */
    @Value("${huasen.aes.key:dj38Ca8F8hag23nD}")
    private String aesKey;

    /** AES初始向量（16字节），对应Node.js的SECRET_AES[1] */
    @Value("${huasen.aes.iv:k4h9HdcXmEr83nsF}")
    private String aesIv;

    private SecretKeySpec secretKeySpec;
    private IvParameterSpec ivParameterSpec;

    @PostConstruct
    public void init() {
        this.secretKeySpec = new SecretKeySpec(
                aesKey.getBytes(StandardCharsets.UTF_8), "AES");
        this.ivParameterSpec = new IvParameterSpec(
                aesIv.getBytes(StandardCharsets.UTF_8));
        log.info("AES加密工具初始化成功");
    }

    /**
     * AES加密
     * 对应Node.js: crypto.createCipheriv('aes-128-cbc', key, iv)
     * 输出Base64编码
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("AES加密失败: {}", e.getMessage(), e);
            throw new RuntimeException("AES加密失败", e);
        }
    }

    /**
     * AES解密
     * 对应Node.js: crypto.createDecipheriv('aes-128-cbc', key, iv)
     * 输入Base64编码的密文
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES解密失败: {}", e.getMessage(), e);
            throw new RuntimeException("AES解密失败", e);
        }
    }
}

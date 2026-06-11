package com.huasen.common.util;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA加解密工具
 * 复制Node.js后端的RSA分段解密逻辑，支持":hs:"分隔符模式
 */
@Component
public class RsaUtil {

    private static final Logger log = LoggerFactory.getLogger(RsaUtil.class);

    /** RSA分段加密的分隔符，与前端保持一致 */
    private static final String SEGMENT_SEPARATOR = ":hs:";

    @Value("${huasen.rsa.private-key:}")
    private String privateKeyBase64;

    @Value("${huasen.rsa.public-key:}")
    private String publicKeyBase64;

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private boolean configured = false;

    @PostConstruct
    public void init() {
        if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
            log.warn("RSA私钥未配置，RSA解密功能将不可用（参数将原样传递）");
            return;
        }
        try {
            // 清理PEM格式的头尾和换行
            String cleanPrivateKey = cleanPemKey(privateKeyBase64);
            byte[] privateKeyBytes = Base64.getDecoder().decode(cleanPrivateKey);
            PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.privateKey = keyFactory.generatePrivate(privateSpec);

            if (publicKeyBase64 != null && !publicKeyBase64.isBlank()) {
                String cleanPublicKey = cleanPemKey(publicKeyBase64);
                byte[] publicKeyBytes = Base64.getDecoder().decode(cleanPublicKey);
                X509EncodedKeySpec publicSpec = new X509EncodedKeySpec(publicKeyBytes);
                this.publicKey = keyFactory.generatePublic(publicSpec);
            }

            this.configured = true;
            log.info("RSA密钥初始化成功");
        } catch (Exception e) {
            log.error("RSA密钥初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 判断RSA是否已配置
     */
    public boolean isConfigured() {
        return configured;
    }

    /**
     * 分段解密（私钥解密）
     * 对应Node.js的rsaDecryptLong('private', text)
     * 前端将长文本分段加密后用":hs:"连接，此方法逐段解密后拼接
     *
     * @param cipherText 用":hs:"分隔的密文段
     * @return 解密后的明文
     */
    public String decryptLong(String cipherText) {
        if (!configured) {
            log.warn("RSA未配置，返回原始输入");
            return cipherText;
        }
        if (cipherText == null || cipherText.isBlank()) {
            return cipherText;
        }

        try {
            String[] segments = cipherText.split(SEGMENT_SEPARATOR);
            StringBuilder result = new StringBuilder();
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            for (String segment : segments) {
                // Node.js中密文是hex编码，需要从hex转为bytes
                byte[] encryptedBytes = hexToBytes(segment);
                byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
                result.append(new String(decryptedBytes, StandardCharsets.UTF_8));
                // 重新初始化cipher用于下一段
                cipher.init(Cipher.DECRYPT_MODE, privateKey);
            }

            return result.toString();
        } catch (Exception e) {
            log.error("RSA解密失败: {}", e.getMessage(), e);
            throw new RuntimeException("RSA解密失败", e);
        }
    }

    /**
     * 清理PEM格式密钥中的头尾标记和换行符
     */
    private String cleanPemKey(String pemKey) {
        return pemKey
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
    }

    /**
     * 十六进制字符串转字节数组
     */
    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}

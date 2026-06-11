package com.huasen.common.service;

import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 七牛云存储服务
 * 当配置了七牛云access-key时自动激活,否则不创建此Bean
 *
 * @author huizhang43
 * @date 2026-06-01
 */
@Service
@ConditionalOnProperty(prefix = "qiniu", name = "access-key")
public class QiniuStorageService {

    @Value("${qiniu.access-key}")
    private String accessKey;

    @Value("${qiniu.secret-key}")
    private String secretKey;

    @Value("${qiniu.bucket}")
    private String bucket;

    @Value("${qiniu.domain}")
    private String domain;

    /**
     * 上传文件到七牛云
     *
     * @param file 待上传的文件
     * @param type 文件类型(用作路径前缀,如 icon/banner/article/img)
     * @return CDN访问URL
     * @throws IOException 上传失败时抛出
     */
    public String upload(MultipartFile file, String type) throws IOException {
        return upload(file.getBytes(), file.getOriginalFilename(), type);
    }

    /**
     * 上传字节数组到七牛云
     *
     * @param data 文件字节数组
     * @param originalFilename 原始文件名(用于提取扩展名)
     * @param type 文件类型(用作路径前缀,如 icon/banner/article/img)
     * @return CDN访问URL
     * @throws IOException 上传失败时抛出
     */
    public String upload(byte[] data, String originalFilename, String type) throws IOException {
        Auth auth = Auth.create(accessKey, secretKey);
        String uploadToken = auth.uploadToken(bucket);

        Configuration cfg = new Configuration(Region.autoRegion());
        UploadManager uploadManager = new UploadManager(cfg);

        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // 添加 huasen/ 前缀，确保所有文件在同一目录下
        String key = "huasen/" + type + "/" + System.currentTimeMillis() + extension;

        try {
            uploadManager.put(data, key, uploadToken);
        } catch (com.qiniu.common.QiniuException e) {
            throw new IOException("Qiniu upload failed: " + e.getMessage(), e);
        }

        return "https://" + domain + "/" + key;
    }

    /**
     * 上传字节数组到七牛云的指定固定key(允许覆盖)
     * 用于需要稳定可预测URL的场景(如预设封面),不使用时间戳命名。
     *
     * @param data 文件字节数组
     * @param key  完整的七牛存储key,如 huasen/tiny-blog-covers/01.jpg
     * @return CDN访问URL
     * @throws IOException 上传失败时抛出
     */
    public String uploadWithKey(byte[] data, String key) throws IOException {
        Auth auth = Auth.create(accessKey, secretKey);
        // token 限定到 bucket:key 作用域,允许覆盖同名文件
        String uploadToken = auth.uploadToken(bucket, key);

        Configuration cfg = new Configuration(Region.autoRegion());
        UploadManager uploadManager = new UploadManager(cfg);

        try {
            uploadManager.put(data, key, uploadToken);
        } catch (com.qiniu.common.QiniuException e) {
            throw new IOException("Qiniu upload failed: " + e.getMessage(), e);
        }

        return buildUrl(key);
    }

    /**
     * 根据七牛存储key拼接完整CDN访问URL
     *
     * @param key 七牛存储key
     * @return CDN访问URL
     */
    public String buildUrl(String key) {
        return "https://" + domain + "/" + key;
    }

    /**
     * 判断七牛云是否已配置(Bean存在即表示已配置)
     *
     * @return true
     */
    public boolean isConfigured() {
        return true;
    }
}

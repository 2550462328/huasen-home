package com.huasen.blog.tiny.migration;

import com.huasen.blog.tiny.service.TinyBlogCoverService;
import com.huasen.common.service.QiniuStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Tiny Blog 预设封面一次性上传工具
 *
 * 将6张预设封面(01.jpg~06.jpg)以固定key上传到七牛云,
 * key形如 huasen/tiny-blog-covers/01.jpg,使CDN URL稳定可预测。
 *
 * 默认禁用,需设置 tiny-blog.cover-upload.enabled=true 手动启用一次性运行。
 * 上传完成后请将开关改回 false。
 */
@Component
@ConditionalOnProperty(name = "tiny-blog.cover-upload.enabled", havingValue = "true")
public class TinyBlogCoverUploader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TinyBlogCoverUploader.class);

    private final QiniuStorageService qiniuStorageService;
    private final TinyBlogCoverService coverService;

    @Value("${tiny-blog.migration.images-source-dir:tiny-blog/static/images}")
    private String imagesSourceDir;

    public TinyBlogCoverUploader(QiniuStorageService qiniuStorageService,
                                 TinyBlogCoverService coverService) {
        this.qiniuStorageService = qiniuStorageService;
        this.coverService = coverService;
    }

    @Override
    public void run(String... args) {
        log.info("===== Tiny Blog 预设封面上传开始 =====");

        if (qiniuStorageService == null) {
            log.error("七牛云未配置,无法上传封面");
            return;
        }

        Path sourceDir = Paths.get(imagesSourceDir);
        if (!Files.isDirectory(sourceDir)) {
            log.error("封面源目录不存在: {}", sourceDir.toAbsolutePath());
            return;
        }

        int success = 0;
        for (String filename : coverService.getCoverFilenames()) {
            Path sourceFile = sourceDir.resolve(filename);
            if (!Files.exists(sourceFile)) {
                log.warn("封面源文件不存在,跳过: {}", sourceFile.toAbsolutePath());
                continue;
            }
            try {
                byte[] data = Files.readAllBytes(sourceFile);
                String key = coverService.buildCoverKey(filename);
                String url = qiniuStorageService.uploadWithKey(data, key);
                log.info("上传成功: {} -> {}", filename, url);
                success++;
            } catch (Exception e) {
                log.error("上传失败: {} - {}", filename, e.getMessage(), e);
            }
        }

        log.info("===== Tiny Blog 预设封面上传完成,成功 {}/{} =====",
                success, coverService.getCoverFilenames().size());
    }
}

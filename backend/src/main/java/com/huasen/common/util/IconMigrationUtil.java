package com.huasen.common.util;

import com.huasen.common.repository.ColumnRepository;
import com.huasen.common.repository.SiteRepository;
import com.huasen.common.service.QiniuStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Icon 图标迁移工具
 * 将历史的本地相对路径图标迁移到七牛云CDN
 *
 * 使用方式：
 * 1. 在 application.yml 添加 huasen.migrate-icons=true
 * 2. 启动应用一次，迁移自动执行
 * 3. 检查日志确认迁移完成
 * 4. 移除配置标志
 */
@Component
@ConditionalOnProperty(name = "huasen.migrate-icons", havingValue = "true")
public class IconMigrationUtil implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IconMigrationUtil.class);

    @Value("${huasen.icon-source-path:origin/huasenjio-compose}")
    private String iconSourcePath;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private ColumnRepository columnRepository;

    @Autowired(required = false)
    private QiniuStorageService qiniuStorageService;

    @Override
    public void run(String... args) {
        if (qiniuStorageService == null) {
            log.error("七牛云服务未配置，无法执行迁移。请先配置 qiniu.* 参数");
            return;
        }

        log.info("===== 开始迁移 Icon 图标到七牛云 =====");

        AtomicInteger totalSites = new AtomicInteger(0);
        AtomicInteger successSites = new AtomicInteger(0);
        AtomicInteger failedSites = new AtomicInteger(0);
        AtomicInteger missingFiles = new AtomicInteger(0);

        AtomicInteger totalColumns = new AtomicInteger(0);
        AtomicInteger successColumns = new AtomicInteger(0);
        AtomicInteger failedColumns = new AtomicInteger(0);

        // 迁移 Site 表的 icon
        migrateSiteIcons(totalSites, successSites, failedSites, missingFiles);

        // 迁移 Column 表的 icon
        migrateColumnIcons(totalColumns, successColumns, failedColumns);

        log.info("===== Icon 迁移完成 =====");
        log.info("Site 表: 总计 {}, 成功 {}, 失败 {}, 文件不存在 {}",
                totalSites.get(), successSites.get(), failedSites.get(), missingFiles.get());
        log.info("Column 表: 总计 {}, 成功 {}, 失败 {}",
                totalColumns.get(), successColumns.get(), failedColumns.get());
    }

    @Transactional
    private void migrateSiteIcons(AtomicInteger total, AtomicInteger success,
                                  AtomicInteger failed, AtomicInteger missing) {
        log.info("开始迁移 Site 表的图标...");

        siteRepository.findAll().forEach(site -> {
            String iconPath = site.getIcon();
            if (iconPath != null && iconPath.startsWith("huasen-store/icon/")) {
                total.incrementAndGet();
                try {
                    String cdnUrl = migrateIcon(iconPath);
                    site.setIcon(cdnUrl);
                    siteRepository.save(site);
                    success.incrementAndGet();
                    log.info("Site[{}] 图标迁移成功: {} -> {}", site.getId(), iconPath, cdnUrl);
                } catch (java.io.FileNotFoundException e) {
                    missing.incrementAndGet();
                    log.warn("Site[{}] 图标文件不存在: {}", site.getId(), iconPath);
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.error("Site[{}] 图标迁移失败: {}, 错误: {}", site.getId(), iconPath, e.getMessage());
                }
            }
        });
    }

    @Transactional
    private void migrateColumnIcons(AtomicInteger total, AtomicInteger success, AtomicInteger failed) {
        log.info("开始迁移 Column 表的图标...");

        columnRepository.findAll().forEach(column -> {
            String iconPath = column.getIcon();
            if (iconPath != null && iconPath.startsWith("huasen-store/icon/")) {
                total.incrementAndGet();
                try {
                    String cdnUrl = migrateIcon(iconPath);
                    column.setIcon(cdnUrl);
                    columnRepository.save(column);
                    success.incrementAndGet();
                    log.info("Column[{}] 图标迁移成功: {} -> {}", column.getId(), iconPath, cdnUrl);
                } catch (java.io.FileNotFoundException e) {
                    failed.incrementAndGet();
                    log.warn("Column[{}] 图标文件不存在: {}", column.getId(), iconPath);
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.error("Column[{}] 图标迁移失败: {}, 错误: {}", column.getId(), iconPath, e.getMessage());
                }
            }
        });
    }

    /**
     * 迁移单个图标文件
     *
     * @param iconPath 相对路径，如 "huasen-store/icon/1709102563855.png"
     * @return 七牛云 CDN URL
     * @throws IOException 文件读取或上传失败
     */
    private String migrateIcon(String iconPath) throws IOException {
        // 构造文件系统路径
        Path filePath = Paths.get(iconSourcePath, iconPath);

        // 检查文件是否存在
        if (!Files.exists(filePath)) {
            throw new java.io.FileNotFoundException("文件不存在: " + filePath);
        }

        // 读取文件字节
        byte[] fileBytes = Files.readAllBytes(filePath);

        // 提取文件名
        String fileName = filePath.getFileName().toString();

        // 上传到七牛云（直接使用字节数组重载方法）
        return qiniuStorageService.upload(fileBytes, fileName, "icon");
    }
}

package com.huasen.migration;

import com.qiniu.common.QiniuException;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 图标文件批量上传工具
 * 将本地图标上传到七牛云并生成SQL更新脚本
 *
 * @author huizhang43
 * @date 2026-06-05
 */
public class IconMigrationTool {

    // 七牛云配置 - 从环境变量读取
    private static final String ACCESS_KEY = System.getenv().getOrDefault("QINIU_ACCESS_KEY", "");
    private static final String SECRET_KEY = System.getenv().getOrDefault("QINIU_SECRET_KEY", "");
    private static final String BUCKET = System.getenv().getOrDefault("QINIU_BUCKET", "");
    private static final String DOMAIN = System.getenv().getOrDefault("QINIU_DOMAIN", "");

    // 本地图标目录
    private static final String ICON_DIR = "deploy/huasen-store/icon/";

    // SQL脚本输出路径
    private static final String SQL_OUTPUT = "deploy/icon-migration.sql";

    // 上传统计
    private static int successCount = 0;
    private static int failCount = 0;
    private static final List<String> failedFiles = new ArrayList<>();
    private static final List<IconMapping> iconMappings = new ArrayList<>();

    static class IconMapping {
        String filename;
        String oldPath;
        String newUrl;

        IconMapping(String filename, String oldPath, String newUrl) {
            this.filename = filename;
            this.oldPath = oldPath;
            this.newUrl = newUrl;
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 图标文件批量上传工具 ==========");
        System.out.println("开始时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("========================================\n");

        try {
            // 1. 扫描本地图标文件
            List<Path> iconFiles = scanIconFiles();
            System.out.println("发现图标文件: " + iconFiles.size() + " 个\n");

            // 2. 批量上传到七牛云
            uploadIcons(iconFiles);

            // 3. 生成SQL更新脚本
            generateSqlScript();

            // 4. 输出摘要报告
            printSummary();

        } catch (Exception e) {
            System.err.println("执行失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 扫描本地图标文件
     */
    private static List<Path> scanIconFiles() throws IOException {
        Path iconDirPath = Paths.get(ICON_DIR);
        if (!Files.exists(iconDirPath)) {
            throw new IOException("图标目录不存在: " + ICON_DIR);
        }

        return Files.walk(iconDirPath, 1)
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.endsWith(".png") || name.endsWith(".jpg")
                        || name.endsWith(".jpeg") || name.endsWith(".gif")
                        || name.endsWith(".svg");
                })
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 批量上传图标到七牛云
     */
    private static void uploadIcons(List<Path> iconFiles) {
        Auth auth = Auth.create(ACCESS_KEY, SECRET_KEY);
        Configuration cfg = new Configuration(Region.autoRegion());
        UploadManager uploadManager = new UploadManager(cfg);

        System.out.println("开始上传...\n");

        for (int i = 0; i < iconFiles.size(); i++) {
            Path iconFile = iconFiles.get(i);
            String filename = iconFile.getFileName().toString();

            try {
                // 上传到七牛云，保持原文件名
                String qiniuKey = "huasen/icon/" + filename;
                String uploadToken = auth.uploadToken(BUCKET, qiniuKey);

                uploadManager.put(iconFile.toFile(), qiniuKey, uploadToken);

                String cdnUrl = "https://" + DOMAIN + "/" + qiniuKey;
                String oldPath = "huasen-store/icon/" + filename;

                iconMappings.add(new IconMapping(filename, oldPath, cdnUrl));
                successCount++;

                System.out.printf("[%d/%d] ✓ %s -> %s\n",
                    i + 1, iconFiles.size(), filename, cdnUrl);

            } catch (QiniuException e) {
                failCount++;
                failedFiles.add(filename);
                System.err.printf("[%d/%d] ✗ %s 上传失败: %s\n",
                    i + 1, iconFiles.size(), filename, e.getMessage());
            }
        }

        System.out.println("\n上传完成！\n");
    }

    /**
     * 生成SQL更新脚本
     */
    private static void generateSqlScript() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(SQL_OUTPUT))) {
            // SQL头部注释
            writer.write("-- 图标迁移 SQL 脚本\n");
            writer.write("-- 生成时间: " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            writer.write("-- 迁移文件数量: " + successCount + "\n");
            writer.write("-- 失败文件数量: " + failCount + "\n");
            writer.write("\n");

            if (!failedFiles.isEmpty()) {
                writer.write("-- 上传失败的文件:\n");
                for (String failed : failedFiles) {
                    writer.write("-- " + failed + "\n");
                }
                writer.write("\n");
            }

            // 更新 Site 表
            writer.write("-- 更新 Site 表图标路径（从本地路径改为七牛云 CDN URL）\n");
            for (IconMapping mapping : iconMappings) {
                writer.write(String.format(
                    "UPDATE Site SET icon = '%s' WHERE icon = '%s';\n",
                    mapping.newUrl, mapping.oldPath));
            }
            writer.write("\n");

            // 更新 ColumnEntity 表
            writer.write("-- 更新 ColumnEntity 表图标路径\n");
            for (IconMapping mapping : iconMappings) {
                writer.write(String.format(
                    "UPDATE ColumnEntity SET icon = '%s' WHERE icon = '%s';\n",
                    mapping.newUrl, mapping.oldPath));
            }
            writer.write("\n");

            // 验证查询
            writer.write("-- 验证迁移结果的查询语句\n");
            writer.write("SELECT COUNT(*) as migrated_count FROM Site WHERE icon LIKE 'https://pcc.huitogo.club/%';\n");
            writer.write("SELECT COUNT(*) as remaining_count FROM Site WHERE icon LIKE 'huasen-store/icon/%';\n");
            writer.write("SELECT COUNT(*) as migrated_count FROM ColumnEntity WHERE icon LIKE 'https://pcc.huitogo.club/%';\n");
            writer.write("SELECT COUNT(*) as remaining_count FROM ColumnEntity WHERE icon LIKE 'huasen-store/icon/%';\n");
        }

        System.out.println("SQL脚本已生成: " + SQL_OUTPUT + "\n");
    }

    /**
     * 输出摘要报告
     */
    private static void printSummary() {
        System.out.println("========================================");
        System.out.println("            批量上传摘要报告");
        System.out.println("========================================");
        System.out.println("总文件数: " + (successCount + failCount));
        System.out.println("成功上传: " + successCount + " 个");
        System.out.println("上传失败: " + failCount + " 个");

        if (!failedFiles.isEmpty()) {
            System.out.println("\n失败文件列表:");
            for (String failed : failedFiles) {
                System.out.println("  - " + failed);
            }
        }

        System.out.println("\nSQL脚本路径: " + SQL_OUTPUT);
        System.out.println("完成时间: " + LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("========================================");
    }
}

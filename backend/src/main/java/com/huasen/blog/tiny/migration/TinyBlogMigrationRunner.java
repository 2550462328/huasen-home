package com.huasen.blog.tiny.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huasen.blog.tiny.entity.TinyBlogCategory;
import com.huasen.blog.tiny.entity.TinyBlogPost;
import com.huasen.blog.tiny.repository.TinyBlogCategoryRepository;
import com.huasen.blog.tiny.repository.TinyBlogPostRepository;
import com.huasen.common.service.QiniuStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

/**
 * Tiny Blog 数据迁移运行器
 *
 * 从tiny-blog/posts/目录读取Markdown文件，解析元数据和内容，
 * 结合statistic.json1中的访问量统计，导入到MySQL数据库。
 *
 * 默认禁用，需设置 tiny-blog.migration.enabled=true 手动启用一次性运行。
 */
@Component
@ConditionalOnProperty(name = "tiny-blog.migration.enabled", havingValue = "true")
public class TinyBlogMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TinyBlogMigrationRunner.class);

    /** BOM字符 (UTF-8 BOM: EF BB BF 解码后为 ﻿) */
    private static final String BOM = "﻿";

    /** 封面图片文件名列表 */
    private static final List<String> COVER_IMAGES = List.of(
            "01.jpg", "02.jpg", "03.jpg", "04.jpg", "05.jpg", "06.jpg"
    );

    private final TinyBlogPostRepository postRepository;
    private final TinyBlogCategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;
    private final QiniuStorageService qiniuStorageService;

    @Value("${tiny-blog.migration.posts-dir:tiny-blog/posts}")
    private String postsDir;

    @Value("${tiny-blog.migration.statistic-file:tiny-blog/statistic.json1}")
    private String statisticFile;

    @Value("${tiny-blog.migration.images-source-dir:tiny-blog/static/images}")
    private String imagesSourceDir;

    public TinyBlogMigrationRunner(TinyBlogPostRepository postRepository,
                                   TinyBlogCategoryRepository categoryRepository,
                                   ObjectMapper objectMapper,
                                   QiniuStorageService qiniuStorageService) {
        this.postRepository = postRepository;
        this.categoryRepository = categoryRepository;
        this.objectMapper = objectMapper;
        this.qiniuStorageService = qiniuStorageService;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("=== Tiny Blog Migration Started ===");

        // Step 1: Upload cover images to Qiniu CDN
        Map<String, String> coverImageUrls = copyCoverImages();

        // Step 2: Load visit count statistics
        Map<String, Integer> visitCountMap = loadStatistics();

        // Step 3: Parse and import all MD files
        Path postsPath = Paths.get(postsDir);
        if (!Files.isDirectory(postsPath)) {
            log.error("Posts directory not found: {}", postsPath.toAbsolutePath());
            return;
        }

        // Category deduplication cache
        Map<String, TinyBlogCategory> categoryCache = new HashMap<>();

        int successCount = 0;
        int failCount = 0;

        try (Stream<Path> files = Files.list(postsPath)) {
            List<Path> mdFiles = files
                    .filter(p -> p.toString().endsWith(".md"))
                    .sorted()
                    .toList();

            log.info("Found {} MD files to migrate", mdFiles.size());

            for (Path mdFile : mdFiles) {
                try {
                    TinyBlogPost post = parseMdFile(mdFile, visitCountMap, categoryCache, coverImageUrls);
                    postRepository.save(post);
                    log.info("Migrated: {} (visits: {})", post.getTitle(), post.getVisitCount());
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to migrate file: {} - {}", mdFile.getFileName(), e.getMessage());
                    failCount++;
                }
            }
        }

        // Update category post counts
        for (TinyBlogCategory category : categoryCache.values()) {
            categoryRepository.save(category);
        }

        log.info("=== Tiny Blog Migration Complete: {} success, {} failed ===", successCount, failCount);
    }

    /**
     * 上传封面图片到七牛云CDN
     *
     * @return Map<filename, CDN URL> 映射表
     */
    private Map<String, String> copyCoverImages() {
        Map<String, String> coverImageUrls = new HashMap<>();

        if (qiniuStorageService == null) {
            log.error("七牛云服务未配置，无法执行迁移");
            return coverImageUrls;
        }

        Path sourceDir = Paths.get(imagesSourceDir);

        if (!Files.isDirectory(sourceDir)) {
            log.warn("Cover images source directory not found: {}", sourceDir.toAbsolutePath());
            return coverImageUrls;
        }

        for (String imageName : COVER_IMAGES) {
            Path sourceFile = sourceDir.resolve(imageName);
            if (Files.exists(sourceFile)) {
                try {
                    byte[] imageData = Files.readAllBytes(sourceFile);
                    String cdnUrl = qiniuStorageService.upload(imageData, imageName, "tiny-blog-covers");
                    coverImageUrls.put(imageName, cdnUrl);
                    log.info("Uploaded cover image: {} -> {}", imageName, cdnUrl);
                } catch (IOException e) {
                    log.error("Failed to upload cover image: {} - {}", imageName, e.getMessage());
                    coverImageUrls.put(imageName, "");
                }
            } else {
                log.warn("Cover image not found: {}", sourceFile);
                coverImageUrls.put(imageName, "");
            }
        }

        return coverImageUrls;
    }

    /**
     * 加载访问量统计数据
     * statistic.json1格式: {"artStat": {"md5hash": {"title": "...", "visitCnt": N, "commentCnt": N}}}
     */
    private Map<String, Integer> loadStatistics() {
        Map<String, Integer> visitCountMap = new HashMap<>();
        Path statisticPath = Paths.get(statisticFile);

        if (!Files.exists(statisticPath)) {
            log.warn("Statistic file not found: {}, all visit counts will be 0", statisticPath.toAbsolutePath());
            return visitCountMap;
        }

        try {
            Map<String, Object> root = objectMapper.readValue(
                    statisticPath.toFile(),
                    new TypeReference<Map<String, Object>>() {}
            );

            Object artStatObj = root.get("artStat");
            if (artStatObj instanceof Map<?, ?> artStat) {
                for (Map.Entry<?, ?> entry : artStat.entrySet()) {
                    String hash = entry.getKey().toString();
                    if (entry.getValue() instanceof Map<?, ?> statData) {
                        Object visitCntObj = statData.get("visitCnt");
                        int visitCnt = 0;
                        if (visitCntObj instanceof Number num) {
                            visitCnt = num.intValue();
                        }
                        visitCountMap.put(hash, visitCnt);
                    }
                }
            }

            log.info("Loaded {} visit count entries from statistic file", visitCountMap.size());
        } catch (IOException e) {
            log.error("Failed to parse statistic file: {}", e.getMessage());
        }

        return visitCountMap;
    }

    /**
     * 解析单个MD文件
     *
     * MD文件格式 (固定6行头部):
     * Line 0: title
     * Line 1: date (YYYY-MM-DD)
     * Line 2: summary
     * Line 3: cover_image filename (e.g., "02.jpg")
     * Line 4: category (e.g., "Java基础")
     * Line 5: author
     * Line 6+: Markdown content body
     */
    private TinyBlogPost parseMdFile(Path mdFile,
                                     Map<String, Integer> visitCountMap,
                                     Map<String, TinyBlogCategory> categoryCache,
                                     Map<String, String> coverImageUrls) throws IOException, ParseException {
        // Read file content with UTF-8 encoding
        String rawContent = Files.readString(mdFile, StandardCharsets.UTF_8);

        // Strip BOM from first line if present
        if (rawContent.startsWith(BOM)) {
            rawContent = rawContent.substring(1);
        }

        // Normalize line endings to LF
        rawContent = rawContent.replace("\r\n", "\n").replace("\r", "\n");

        String[] lines = rawContent.split("\n");

        if (lines.length < 6) {
            throw new IllegalArgumentException("MD file has fewer than 6 metadata lines: " + mdFile.getFileName());
        }

        // Extract metadata from lines 0-5
        String title = lines[0].trim();
        String dateStr = lines[1].trim();
        String summary = lines[2].trim();
        String coverImage = lines[3].trim().replace(" ", "");
        String categoryName = lines[4].trim().replace(" ", "").replace("\r", "").replace("\n", "");
        String author = lines[5].trim();

        // Content is lines 6+ joined with newline
        String content = lines.length > 6
                ? String.join("\n", Arrays.copyOfRange(lines, 6, lines.length))
                : "";

        // Parse date
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        Date publishDate = dateFormat.parse(dateStr);

        // Resolve category (deduplication via cache)
        TinyBlogCategory category = categoryCache.computeIfAbsent(categoryName, name -> {
            return categoryRepository.findByName(name)
                    .orElseGet(() -> {
                        TinyBlogCategory newCategory = new TinyBlogCategory();
                        newCategory.setName(name);
                        newCategory.setPostCount(0);
                        return categoryRepository.save(newCategory);
                    });
        });
        category.setPostCount(category.getPostCount() + 1);

        // Compute MD5 hash of filename (without .md extension) for visit count lookup
        String filename = mdFile.getFileName().toString();
        String filenameWithoutExt = filename.substring(0, filename.lastIndexOf(".md"));
        String md5Hash = DigestUtils.md5DigestAsHex(filenameWithoutExt.getBytes(StandardCharsets.UTF_8));

        // Lookup visit count
        int visitCount = visitCountMap.getOrDefault(md5Hash, 0);

        // Build post entity
        TinyBlogPost post = new TinyBlogPost();
        post.setTitle(title);
        post.setPublishDate(publishDate);
        post.setSummary(summary);
        post.setCoverImage(coverImageUrls.getOrDefault(coverImage, ""));
        post.setCategory(category);
        post.setAuthor(author);
        post.setContent(content);
        post.setVisitCount(visitCount);
        post.setCreateTime(new Date());
        post.setUpdateTime(new Date());

        return post;
    }
}

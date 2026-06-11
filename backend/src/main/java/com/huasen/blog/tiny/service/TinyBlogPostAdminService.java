package com.huasen.blog.tiny.service;

import com.huasen.blog.tiny.entity.TinyBlogCategory;
import com.huasen.blog.tiny.entity.TinyBlogPost;
import com.huasen.blog.tiny.repository.TinyBlogCategoryRepository;
import com.huasen.blog.tiny.repository.TinyBlogPostRepository;
import com.huasen.common.service.ai.ArticleSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tiny Blog 文章后台管理服务
 * 提供文章CRUD、搜索等后台操作
 * 维护分类的postCount计数一致性
 */
@Service
public class TinyBlogPostAdminService {

    private static final Logger logger = LoggerFactory.getLogger(TinyBlogPostAdminService.class);

    @Autowired
    private TinyBlogPostRepository tinyBlogPostRepository;

    @Autowired
    private TinyBlogCategoryRepository tinyBlogCategoryRepository;

    @Autowired
    private ArticleSummaryService articleSummaryService;

    /**
     * 后台文章分页列表
     * @param page 页码(从1开始)
     * @param size 每页条数
     * @return 分页结果Map
     */
    public Map<String, Object> findByPage(int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page - 1), size,
                Sort.by(Sort.Direction.DESC, "createTime"));

        Page<TinyBlogPost> result = tinyBlogPostRepository.findAll(pageable);

        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getContent());
        data.put("total", result.getTotalElements());
        data.put("pages", result.getTotalPages());
        data.put("pageNo", page);
        data.put("pageSize", size);
        return data;
    }

    /**
     * 按标题关键词搜索文章
     * @param keyword 关键词
     * @param page 页码
     * @param size 每页条数
     * @return 分页搜索结果
     */
    public Map<String, Object> searchByTitle(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page - 1), size,
                Sort.by(Sort.Direction.DESC, "createTime"));

        Page<TinyBlogPost> result = tinyBlogPostRepository.findByTitleContaining(keyword, pageable);

        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getContent());
        data.put("total", result.getTotalElements());
        data.put("pages", result.getTotalPages());
        data.put("pageNo", page);
        data.put("pageSize", size);
        return data;
    }

    /**
     * 根据ID查询文章详情
     * @param id 文章ID
     * @return 文章实体
     */
    public TinyBlogPost findById(Long id) {
        return tinyBlogPostRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文章不存在: " + id));
    }

    /**
     * 创建文章
     * 从params提取字段,设置时间,递增分类postCount
     * @param params 文章参数
     * @return 保存后的文章实体
     */
    @Transactional
    public TinyBlogPost save(Map<String, Object> params) {
        TinyBlogPost post = new TinyBlogPost();
        post.setTitle((String) params.get("title"));
        post.setSummary((String) params.get("summary"));
        post.setCoverImage((String) params.get("coverImage"));
        post.setAuthor((String) params.get("author"));
        post.setContent((String) params.get("content"));

        // AI自动生成摘要 (Phase 12: 如果summary为空，自动调用阿里百炼生成)
        String summary = post.getSummary();
        if (summary == null || summary.isBlank()) {
            try {
                String content = post.getContent();
                if (content != null && !content.isBlank()) {
                    String aiSummary = articleSummaryService.generateSummary(null, content);  // articleId is null (not saved yet)
                    post.setSummary(aiSummary);
                    logger.info("AI生成摘要成功, title={}, summaryLength={}", post.getTitle(), aiSummary.length());
                }
            } catch (Exception e) {
                // 捕获所有异常，避免影响文章保存
                logger.warn("AI摘要生成失败(不影响保存), title={}: {}", post.getTitle(), e.getMessage());
                // ArticleSummaryService already returned fallback; no need to set default here
            }
        }

        // 设置发布日期
        Date publishDate = parseDate(params.get("publishDate"));
        post.setPublishDate(publishDate != null ? publishDate : new Date());

        // 设置时间
        Date now = new Date();
        post.setCreateTime(now);
        post.setUpdateTime(now);
        post.setVisitCount(0);

        // 处理分类关联
        Long categoryId = params.get("categoryId") != null
                ? ((Number) params.get("categoryId")).longValue() : null;
        if (categoryId != null) {
            Optional<TinyBlogCategory> category = tinyBlogCategoryRepository.findById(categoryId);
            if (category.isPresent()) {
                post.setCategory(category.get());
                // 递增分类文章数
                tinyBlogCategoryRepository.incrementPostCount(categoryId);
            }
        }

        // 标题长度校验(T-03-04: validate title length <= 255)
        if (post.getTitle() != null && post.getTitle().length() > 255) {
            post.setTitle(post.getTitle().substring(0, 255));
        }

        return tinyBlogPostRepository.save(post);
    }

    /**
     * 更新文章
     * 如果分类变更,维护新旧分类的postCount
     * @param id 文章ID
     * @param params 更新参数
     * @return 更新后的文章实体
     */
    @Transactional
    public TinyBlogPost update(Long id, Map<String, Object> params) {
        TinyBlogPost post = tinyBlogPostRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文章不存在: " + id));

        if (params.containsKey("title")) {
            String title = (String) params.get("title");
            // 标题长度校验(T-03-04)
            if (title != null && title.length() > 255) {
                title = title.substring(0, 255);
            }
            post.setTitle(title);
        }
        if (params.containsKey("summary")) {
            post.setSummary((String) params.get("summary"));
        }
        if (params.containsKey("coverImage")) {
            post.setCoverImage((String) params.get("coverImage"));
        }
        if (params.containsKey("author")) {
            post.setAuthor((String) params.get("author"));
        }
        if (params.containsKey("content")) {
            post.setContent((String) params.get("content"));
        }
        if (params.containsKey("publishDate") && params.get("publishDate") != null) {
            Date publishDate = parseDate(params.get("publishDate"));
            if (publishDate != null) {
                post.setPublishDate(publishDate);
            }
        }

        // 处理分类变更
        if (params.containsKey("categoryId")) {
            Long newCategoryId = params.get("categoryId") != null
                    ? ((Number) params.get("categoryId")).longValue() : null;
            Long oldCategoryId = post.getCategory() != null ? post.getCategory().getId() : null;

            // 分类发生变更时维护postCount
            if (!java.util.Objects.equals(oldCategoryId, newCategoryId)) {
                // 旧分类递减
                if (oldCategoryId != null) {
                    tinyBlogCategoryRepository.decrementPostCount(oldCategoryId);
                }
                // 新分类递增
                if (newCategoryId != null) {
                    Optional<TinyBlogCategory> newCategory = tinyBlogCategoryRepository.findById(newCategoryId);
                    if (newCategory.isPresent()) {
                        post.setCategory(newCategory.get());
                        tinyBlogCategoryRepository.incrementPostCount(newCategoryId);
                    } else {
                        post.setCategory(null);
                    }
                } else {
                    post.setCategory(null);
                }
            }
        }

        // 更新时间
        post.setUpdateTime(new Date());

        return tinyBlogPostRepository.save(post);
    }

    /**
     * 安全解析发布日期，兼容多种入参类型
     * 前端 JSON 传来的多为字符串(yyyy-MM-dd)，也兼容 Date 与时间戳
     * @param value 原始入参
     * @return 解析后的 Date，无法解析返回 null
     */
    private Date parseDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof Number) {
            return new Date(((Number) value).longValue());
        }
        String str = value.toString().trim();
        if (str.isEmpty()) {
            return null;
        }
        // 优先按完整日期时间解析，失败则回退到纯日期(取前10位)
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss"};
        for (String pattern : patterns) {
            if (str.length() >= pattern.length()) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                    sdf.setLenient(false);
                    return sdf.parse(str);
                } catch (ParseException ignored) {
                    // 尝试下一个格式
                }
            }
        }
        // 回退: 取前10位按 yyyy-MM-dd 解析(兼容 ISO 串如 2026-06-10T00:00:00.000Z)
        if (str.length() >= 10) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                sdf.setLenient(false);
                return sdf.parse(str.substring(0, 10));
            } catch (ParseException ignored) {
                // 落到下方告警
            }
        }
        logger.warn("无法解析发布日期: {}", str);
        return null;
    }

    /**
     * 删除文章
     * 递减所属分类的postCount
     * @param id 文章ID
     */
    @Transactional
    public void delete(Long id) {
        TinyBlogPost post = tinyBlogPostRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文章不存在: " + id));

        // 递减分类文章数
        if (post.getCategory() != null) {
            tinyBlogCategoryRepository.decrementPostCount(post.getCategory().getId());
        }

        tinyBlogPostRepository.delete(post);
    }
}

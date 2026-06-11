package com.huasen.blog.sharon.service;

import com.huasen.blog.sharon.entity.BlogCategory;
import com.huasen.blog.sharon.entity.BlogPost;
import com.huasen.blog.sharon.repository.BlogCategoryRepository;
import com.huasen.blog.sharon.repository.BlogPostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 博客文章后台管理服务
 * 提供文章CRUD、状态管理、搜索等后台操作
 * ES同步: save/update/delete后同步ES索引(失败不影响主操作)
 */
@Service
public class BlogPostAdminService {

    private static final Logger logger = LoggerFactory.getLogger(BlogPostAdminService.class);

    @Autowired
    private BlogPostRepository blogPostRepository;

    @Autowired
    private BlogCategoryRepository blogCategoryRepository;

    @Autowired
    private BlogSearchService blogSearchService;

    /**
     * 后台文章分页列表
     * @param page 页码(从1开始)
     * @param size 每页条数
     * @param status 状态筛选(null=全部, 0=已发布, 1=草稿, 2=回收站)
     * @return 分页结果
     */
    public Map<String, Object> findByPage(int page, int size, Integer status) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page - 1), size,
                Sort.by(Sort.Direction.DESC, "postDate"));

        Page<BlogPost> result;
        if (status != null) {
            result = blogPostRepository.findByPostStatus(status, pageable);
        } else {
            result = blogPostRepository.findAll(pageable);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getContent());
        data.put("total", result.getTotalElements());
        data.put("pages", result.getTotalPages());
        data.put("pageNo", page);
        data.put("pageSize", size);
        return data;
    }

    /**
     * 后台文章详情(供编辑器读取)
     * @param postId 文章ID
     * @return 文章实体
     */
    public BlogPost findById(Long postId) {
        return blogPostRepository.findByPostId(postId)
                .orElseThrow(() -> new RuntimeException("文章不存在: " + postId));
    }

    /**
     * 后台文章搜索(数据库LIKE查询)
     * @param keyword 关键词(标题模糊匹配)
     * @param categoryId 分类ID(可选)
     * @param status 状态筛选(可选)
     * @param page 页码
     * @param size 每页条数
     * @return 分页搜索结果
     */
    public Map<String, Object> searchPosts(String keyword, Long categoryId,
                                           Integer status, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page - 1), size,
                Sort.by(Sort.Direction.DESC, "postDate"));

        Page<BlogPost> result;

        if (categoryId != null && status != null) {
            result = blogPostRepository.searchByKeywordAndCategoryAndStatus(
                    keyword, categoryId, status, pageable);
        } else if (categoryId != null) {
            result = blogPostRepository.searchByKeywordAndCategory(
                    keyword, categoryId, pageable);
        } else if (status != null) {
            result = blogPostRepository.findByPostTitleContainingAndPostStatus(
                    keyword, status, pageable);
        } else {
            result = blogPostRepository.findByPostTitleContaining(keyword, pageable);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getContent());
        data.put("total", result.getTotalElements());
        data.put("pages", result.getTotalPages());
        data.put("pageNo", page);
        data.put("pageSize", size);
        return data;
    }

    /**
     * 创建文章
     * 从params提取字段, 自动生成摘要和时间
     * @param params 文章参数
     * @return 保存后的文章实体
     */
    @Transactional
    public BlogPost save(Map<String, Object> params) {
        BlogPost post = new BlogPost();
        post.setPostTitle((String) params.get("postTitle"));
        post.setPostContentMd((String) params.get("postContentMd"));
        post.setPostContent((String) params.get("postContent"));
        post.setPostUrl((String) params.get("postUrl"));
        post.setPostThumbnail((String) params.get("postThumbnail"));
        post.setPostType("post");
        post.setPostStatus(params.get("postStatus") != null
                ? ((Number) params.get("postStatus")).intValue() : 0);
        post.setAllowComment(params.get("allowComment") != null
                ? ((Number) params.get("allowComment")).intValue() : 1);
        post.setCustomTpl((String) params.get("customTpl"));

        // 自动生成摘要: 截取Markdown前120字
        String summary = (String) params.get("postSummary");
        if (summary == null || summary.isEmpty()) {
            String md = post.getPostContentMd();
            if (md != null && md.length() > 120) {
                summary = md.substring(0, 120);
            } else {
                summary = md;
            }
        }
        post.setPostSummary(summary);

        // 设置时间
        Date now = new Date();
        post.setPostDate(now);
        post.setPostUpdate(now);
        post.setPostViews(0L);

        // 处理分类关联
        resolveCategories(post, params);

        // 处理标签关联
        // tag 数据未迁移(08-CONTEXT 决策),此处跳过

        BlogPost savedPost = blogPostRepository.save(post);

        // 同步ES索引(失败不影响主操作)
        syncToElasticsearch(savedPost);

        return savedPost;
    }

    /**
     * 更新文章
     * 保留原有postViews, 更新postUpdate
     * @param postId 文章ID
     * @param params 更新参数
     * @return 更新后的文章实体
     */
    @Transactional
    public BlogPost update(Long postId, Map<String, Object> params) {
        BlogPost post = blogPostRepository.findByPostId(postId)
                .orElseThrow(() -> new RuntimeException("文章不存在: " + postId));

        if (params.containsKey("postTitle")) {
            post.setPostTitle((String) params.get("postTitle"));
        }
        if (params.containsKey("postContentMd")) {
            post.setPostContentMd((String) params.get("postContentMd"));
        }
        if (params.containsKey("postContent")) {
            post.setPostContent((String) params.get("postContent"));
        }
        if (params.containsKey("postUrl")) {
            post.setPostUrl((String) params.get("postUrl"));
        }
        if (params.containsKey("postThumbnail")) {
            post.setPostThumbnail((String) params.get("postThumbnail"));
        }
        if (params.containsKey("postStatus")) {
            post.setPostStatus(((Number) params.get("postStatus")).intValue());
        }
        if (params.containsKey("allowComment")) {
            post.setAllowComment(((Number) params.get("allowComment")).intValue());
        }
        if (params.containsKey("customTpl")) {
            post.setCustomTpl((String) params.get("customTpl"));
        }
        if (params.containsKey("postSummary")) {
            post.setPostSummary((String) params.get("postSummary"));
        }

        // 更新时间
        post.setPostUpdate(new Date());

        // 更新分类关联
        if (params.containsKey("cateIds")) {
            resolveCategories(post, params);
        }

        // 更新标签关联
        // tag 数据未迁移(08-CONTEXT 决策),此处跳过

        BlogPost updatedPost = blogPostRepository.save(post);

        // 同步ES索引(失败不影响主操作)
        syncToElasticsearch(updatedPost);

        return updatedPost;
    }

    /**
     * 修改文章状态
     * @param postId 文章ID
     * @param status 新状态(0=已发布, 1=草稿, 2=回收站)
     * @return 更新后的文章
     */
    @Transactional
    public BlogPost updateStatus(Long postId, Integer status) {
        BlogPost post = blogPostRepository.findByPostId(postId)
                .orElseThrow(() -> new RuntimeException("文章不存在: " + postId));
        post.setPostStatus(status);
        post.setPostUpdate(new Date());
        return blogPostRepository.save(post);
    }

    /**
     * 永久删除文章
     * @param postId 文章ID
     */
    @Transactional
    public void remove(Long postId) {
        BlogPost post = blogPostRepository.findByPostId(postId)
                .orElseThrow(() -> new RuntimeException("文章不存在: " + postId));
        blogPostRepository.delete(post);

        // 从ES删除索引(失败不影响主操作)
        deleteFromElasticsearch(postId);
    }

    /**
     * 解析并设置分类关联
     */
    @SuppressWarnings("unchecked")
    private void resolveCategories(BlogPost post, Map<String, Object> params) {
        Object cateIdsObj = params.get("cateIds");
        if (cateIdsObj instanceof List) {
            List<Number> cateIds = (List<Number>) cateIdsObj;
            Set<BlogCategory> categories = cateIds.stream()
                    .map(id -> blogCategoryRepository.findById(id.longValue()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());
            post.setCategories(categories);
        } else {
            post.setCategories(new HashSet<>());
        }
    }

    /**
     * 同步文章到ES索引
     * 包裹在try-catch中, ES失败不影响主操作
     */
    private void syncToElasticsearch(BlogPost post) {
        try {
            if (blogSearchService.isAvailable()) {
                blogSearchService.indexPost(post);
            }
        } catch (Exception e) {
            logger.warn("ES索引同步失败(不影响主操作), postId={}: {}", post.getPostId(), e.getMessage());
        }
    }

    /**
     * 从ES删除文章索引
     * 包裹在try-catch中, ES失败不影响主操作
     */
    private void deleteFromElasticsearch(Long postId) {
        try {
            if (blogSearchService.isAvailable()) {
                blogSearchService.deletePost(postId);
            }
        } catch (Exception e) {
            logger.warn("ES索引删除失败(不影响主操作), postId={}: {}", postId, e.getMessage());
        }
    }
}

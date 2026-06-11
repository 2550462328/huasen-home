package com.huasen.blog.sharon.controller;

import com.huasen.blog.sharon.entity.BlogPost;
import com.huasen.blog.sharon.entity.BlogTag;
import com.huasen.blog.sharon.service.BlogPostService;
import com.huasen.blog.sharon.service.BlogTagService;
import com.huasen.common.dto.HuasenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 博客标签前台控制器
 * 公开访问,无需认证(D-09)
 * 路径前缀: /api/blog-sharon/tags (D-07)
 */
@RestController
@RequestMapping("/blog-sharon/tags")
public class BlogTagController {

    @Autowired
    private BlogTagService blogTagService;

    @Autowired
    private BlogPostService blogPostService;

    /**
     * 所有标签列表
     * GET /api/blog-sharon/tags/
     */
    @GetMapping("/")
    public ResponseEntity<HuasenResponse> findAll() {
        List<BlogTag> tags = blogTagService.findAll();
        return HuasenResponse.success(tags, "查询标签列表成功");
    }

    /**
     * 单个标签详情+关联文章
     * GET /api/blog-sharon/tags/{tagUrl}
     */
    @GetMapping("/{tagUrl}")
    public ResponseEntity<HuasenResponse> findByTagUrl(
            @PathVariable String tagUrl,
            @RequestParam(defaultValue = "1") int page) {

        Optional<BlogTag> tag = blogTagService.findByTagUrl(tagUrl);
        if (tag.isEmpty()) {
            return HuasenResponse.error("标签不存在");
        }

        Page<BlogPost> posts = blogPostService.findPostsByTag(tag.get(), page);
        Map<String, Object> result = new HashMap<>();
        result.put("tag", tag.get());
        result.put("posts", posts.getContent());
        result.put("totalElements", posts.getTotalElements());
        result.put("totalPages", posts.getTotalPages());
        result.put("currentPage", page);
        return HuasenResponse.success(result, "查询标签详情成功");
    }
}

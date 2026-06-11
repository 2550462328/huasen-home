package com.huasen.blog.tiny.controller;

import com.huasen.blog.tiny.entity.TinyBlogPost;
import com.huasen.blog.tiny.service.TinyBlogPostService;
import com.huasen.common.dto.HuasenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tiny Blog 文章前台控制器
 * 公开访问,无需认证(D-15)
 * 路径前缀: /api/tiny-blog/posts
 */
@RestController
@RequestMapping("/tiny-blog/posts")
public class TinyBlogPostController {

    @Autowired
    private TinyBlogPostService tinyBlogPostService;

    /**
     * 文章分页列表
     * GET /api/tiny-blog/posts/page/{page}?size=10&categoryId=&keyword=
     * @param page 页码(从1开始)
     * @param size 每页条数(默认10)
     * @param categoryId 分类ID(可选)
     * @param keyword 标题关键词(可选,按标题模糊检索)
     */
    @GetMapping("/page/{page}")
    public ResponseEntity<HuasenResponse> findByPage(
            @PathVariable int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {

        Page<TinyBlogPost> posts = tinyBlogPostService.findByPage(page, size, categoryId, keyword);
        Map<String, Object> result = new HashMap<>();
        result.put("content", posts.getContent());
        result.put("totalElements", posts.getTotalElements());
        result.put("totalPages", posts.getTotalPages());
        result.put("currentPage", page);
        return HuasenResponse.success(result, "查询文章列表成功");
    }

    /**
     * 文章详情(递增访问量)
     * GET /api/tiny-blog/posts/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<HuasenResponse> findById(@PathVariable Long id) {
        Optional<TinyBlogPost> post = tinyBlogPostService.findById(id);
        if (post.isEmpty()) {
            return HuasenResponse.error("文章不存在");
        }
        return HuasenResponse.success(post.get(), "查询文章详情成功");
    }
}

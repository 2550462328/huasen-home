package com.huasen.blog.sharon.controller;

import com.huasen.blog.sharon.dto.BlogPostVO;
import com.huasen.blog.sharon.entity.BlogPost;
import com.huasen.blog.sharon.mapper.BlogPostMapper;
import com.huasen.blog.sharon.service.BlogPostService;
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
 * 博客文章前台控制器
 * 公开访问,无需认证(D-09)
 * 路径前缀: /api/blog-sharon/posts (D-07)
 */
@RestController
@RequestMapping("/blog-sharon/posts")
public class BlogPostController {

    @Autowired
    private BlogPostService blogPostService;

    @Autowired
    private BlogPostMapper blogPostMapper;

    /**
     * 文章分页列表
     * GET /api/blog-sharon/posts/page/{page}/{tab}
     * @param page 页码(从1开始)
     * @param tab 排序: 0=oldest, 1=newest, 2=hottest
     * @param categoryId 可选,分类ID(筛选该分类及其所有子分类下的文章)
     */
    @GetMapping("/page/{page}/{tab}")
    public ResponseEntity<HuasenResponse> findByPage(
            @PathVariable int page,
            @PathVariable int tab,
            @RequestParam(value = "categoryId", required = false) Long categoryId) {

        Page<BlogPost> posts = (categoryId != null)
                ? blogPostService.findPublishedPostsByCategory(categoryId, page, tab)
                : blogPostService.findPublishedPosts(page, tab);
        List<BlogPostVO> voList = blogPostMapper.toVOList(posts.getContent());

        Map<String, Object> result = new HashMap<>();
        result.put("content", voList);
        result.put("totalElements", posts.getTotalElements());
        result.put("totalPages", posts.getTotalPages());
        result.put("currentPage", page);
        return HuasenResponse.success(result, "查询文章列表成功");
    }

    /**
     * 文章详情(递增访问量)
     * GET /api/blog-sharon/posts/{postId}
     */
    @GetMapping("/{postId}")
    public ResponseEntity<HuasenResponse> findById(@PathVariable Long postId) {
        Optional<BlogPost> post = blogPostService.findPostById(postId);
        if (post.isEmpty()) {
            return HuasenResponse.error("文章不存在");
        }
        return HuasenResponse.success(blogPostMapper.toDetailVO(post.get()), "查询文章详情成功");
    }
}

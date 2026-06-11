package com.huasen.blog.sharon.controller.admin;

import com.huasen.blog.sharon.service.BlogPostAdminService;
import com.huasen.common.dto.HuasenResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 博客文章后台管理控制器
 * 路径: /api/blog-sharon/admin/posts
 * 需要管理员JWT认证(code >= 2)
 */
@RestController
@RequestMapping("/blog-sharon/admin/posts")
public class BlogPostAdminController {

    @Autowired
    private BlogPostAdminService blogPostAdminService;

    /**
     * 后台文章列表(分页+状态筛选)
     * GET /api/blog-sharon/admin/posts?page=1&size=10&status=0
     */
    @GetMapping
    public ResponseEntity<HuasenResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        Map<String, Object> result = blogPostAdminService.findByPage(page, size, status);
        return HuasenResponse.success(result, "查询文章列表成功");
    }

    /**
     * 后台文章详情
     * GET /api/blog-sharon/admin/posts/{postId}
     */
    @GetMapping("/{postId}")
    public ResponseEntity<HuasenResponse> detail(
            @PathVariable Long postId,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        return HuasenResponse.success(blogPostAdminService.findById(postId), "查询文章详情成功");
    }

    /**
     * 后台文章搜索
     * GET /api/blog-sharon/admin/posts/search?keyword=xxx&categoryId=1&status=0&page=1&size=10
     */
    @GetMapping("/search")
    public ResponseEntity<HuasenResponse> search(
            @RequestParam String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        Map<String, Object> result = blogPostAdminService.searchPosts(
                keyword, categoryId, status, page, size);
        return HuasenResponse.success(result, "搜索文章成功");
    }

    /**
     * 创建文章
     * POST /api/blog-sharon/admin/posts
     */
    @PostMapping
    public ResponseEntity<HuasenResponse> create(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        return HuasenResponse.success(blogPostAdminService.save(params), "创建文章成功");
    }

    /**
     * 更新文章
     * PUT /api/blog-sharon/admin/posts/{postId}
     */
    @PutMapping("/{postId}")
    public ResponseEntity<HuasenResponse> update(
            @PathVariable Long postId,
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        return HuasenResponse.success(blogPostAdminService.update(postId, params), "更新文章成功");
    }

    /**
     * 修改文章状态(回收站/恢复)
     * PUT /api/blog-sharon/admin/posts/{postId}/status
     */
    @PutMapping("/{postId}/status")
    public ResponseEntity<HuasenResponse> updateStatus(
            @PathVariable Long postId,
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        Integer status = ((Number) params.get("status")).intValue();
        return HuasenResponse.success(
                blogPostAdminService.updateStatus(postId, status), "修改文章状态成功");
    }

    /**
     * 永久删除文章
     * DELETE /api/blog-sharon/admin/posts/{postId}
     */
    @DeleteMapping("/{postId}")
    public ResponseEntity<HuasenResponse> delete(
            @PathVariable Long postId,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        blogPostAdminService.remove(postId);
        return HuasenResponse.success(new java.util.HashMap<>(), "删除文章成功");
    }

    /**
     * 检查管理员权限
     * 要求JWT code >= 2 (管理员级别)
     * @return null表示权限通过, 非null表示权限不足的响应
     */
    private ResponseEntity<HuasenResponse> checkAdminPermission(HttpServletRequest request) {
        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 2) {
            return HuasenResponse.forbidden("权限不足");
        }
        return null;
    }
}

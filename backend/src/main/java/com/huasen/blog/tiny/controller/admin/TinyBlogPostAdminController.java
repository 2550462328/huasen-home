package com.huasen.blog.tiny.controller.admin;

import com.huasen.blog.tiny.entity.TinyBlogPost;
import com.huasen.blog.tiny.service.TinyBlogPostAdminService;
import com.huasen.common.dto.HuasenResponse;
import com.huasen.common.service.ai.ArticleSummaryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Tiny Blog 文章后台管理控制器
 * 路径: /api/tiny-blog/admin/posts
 * 需要管理员JWT认证(code >= 2)
 */
@RestController
@RequestMapping("/tiny-blog/admin/posts")
public class TinyBlogPostAdminController {

    @Autowired
    private TinyBlogPostAdminService tinyBlogPostAdminService;

    @Autowired
    private ArticleSummaryService articleSummaryService;

    /**
     * 后台文章列表(分页)
     * GET /api/tiny-blog/admin/posts?page=1&size=10
     */
    @GetMapping
    public ResponseEntity<HuasenResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        Map<String, Object> result = tinyBlogPostAdminService.findByPage(page, size);
        return HuasenResponse.success(result, "查询文章列表成功");
    }

    /**
     * 后台文章搜索
     * GET /api/tiny-blog/admin/posts/search?keyword=xxx&page=1&size=15
     */
    @GetMapping("/search")
    public ResponseEntity<HuasenResponse> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        Map<String, Object> result = tinyBlogPostAdminService.searchByTitle(keyword, page, size);
        return HuasenResponse.success(result, "搜索文章成功");
    }

    /**
     * 获取单篇文章详情
     * GET /api/tiny-blog/admin/posts/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<HuasenResponse> getById(
            @PathVariable Long id,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        TinyBlogPost post = tinyBlogPostAdminService.findById(id);
        return HuasenResponse.success(post, "查询文章详情成功");
    }

    /**
     * 创建文章
     * POST /api/tiny-blog/admin/posts
     */
    @PostMapping
    public ResponseEntity<HuasenResponse> create(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        TinyBlogPost post = tinyBlogPostAdminService.save(params);
        return HuasenResponse.success(post, "创建文章成功");
    }

    /**
     * 更新文章
     * PUT /api/tiny-blog/admin/posts/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<HuasenResponse> update(
            @PathVariable Long id,
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        TinyBlogPost post = tinyBlogPostAdminService.update(id, params);
        return HuasenResponse.success(post, "更新文章成功");
    }

    /**
     * 删除文章
     * DELETE /api/tiny-blog/admin/posts/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<HuasenResponse> delete(
            @PathVariable Long id,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        tinyBlogPostAdminService.delete(id);
        return HuasenResponse.success(new HashMap<>(), "删除文章成功");
    }

    /**
     * 测试 AI 调用是否正常
     * GET /api/tiny-blog/admin/posts/test-ai
     */
    @GetMapping("/test-ai")
    public ResponseEntity<HuasenResponse> testAi(HttpServletRequest request) {
        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        try {
            String testContent = "这是一篇测试文章。主要讨论了Spring Boot框架在企业级应用开发中的优势，包括自动化配置、内嵌服务器、微服务支持等特性。文章详细介绍了依赖注入、AOP切面编程、事务管理等核心概念，并通过实际案例展示了如何构建RESTful API。最后总结了Spring Boot在提升开发效率和代码质量方面的显著作用。";
            String summary = articleSummaryService.generateSummary(null, testContent);
            Map<String, Object> data = new HashMap<>();
            data.put("summary", summary);
            data.put("length", summary.codePointCount(0, summary.length()));
            return HuasenResponse.success(data, "AI测试成功");
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("type", e.getClass().getSimpleName());
            return HuasenResponse.error("AI调用失败: " + e.getMessage());
        }
    }

    /**
     * 预览/试生成摘要(不落库)
     * 用于导入文章时立即代入摘要,避免等到保存才生成
     * POST /api/tiny-blog/admin/posts/summary-preview
     * body: { content: "..." }
     */
    @PostMapping("/summary-preview")
    public ResponseEntity<HuasenResponse> previewSummary(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        String content = (String) params.get("content");
        if (content == null || content.isBlank()) {
            return HuasenResponse.success(Map.of("summary", ""), "内容为空,无需生成");
        }
        // articleId 为 null:尚未落库
        String summary = articleSummaryService.generateSummary(null, content);
        Map<String, Object> data = new HashMap<>();
        data.put("summary", summary != null ? summary : "");
        return HuasenResponse.success(data, "摘要生成完成");
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

package com.huasen.blog.sharon.controller.admin;

import com.huasen.blog.sharon.entity.BlogTag;
import com.huasen.blog.sharon.service.BlogTagAdminService;
import com.huasen.common.dto.HuasenResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 博客标签后台管理控制器
 * 路径: /api/blog-sharon/admin/tags
 * 需要管理员JWT认证(code >= 2)
 */
@RestController
@RequestMapping("/blog-sharon/admin/tags")
public class BlogTagAdminController {

    @Autowired
    private BlogTagAdminService blogTagAdminService;

    /**
     * 所有标签列表
     * GET /api/blog-sharon/admin/tags
     */
    @GetMapping
    public ResponseEntity<HuasenResponse> list(HttpServletRequest request) {
        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        List<BlogTag> tags = blogTagAdminService.findAll();
        return HuasenResponse.success(tags, "查询标签列表成功");
    }

    /**
     * 创建标签
     * POST /api/blog-sharon/admin/tags
     */
    @PostMapping
    public ResponseEntity<HuasenResponse> create(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        BlogTag tag = blogTagAdminService.save(params);
        return HuasenResponse.success(tag, "创建标签成功");
    }

    /**
     * 更新标签
     * PUT /api/blog-sharon/admin/tags/{tagId}
     */
    @PutMapping("/{tagId}")
    public ResponseEntity<HuasenResponse> update(
            @PathVariable Long tagId,
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        BlogTag tag = blogTagAdminService.update(tagId, params);
        return HuasenResponse.success(tag, "更新标签成功");
    }

    /**
     * 删除标签
     * DELETE /api/blog-sharon/admin/tags/{tagId}
     */
    @DeleteMapping("/{tagId}")
    public ResponseEntity<HuasenResponse> delete(
            @PathVariable Long tagId,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        blogTagAdminService.remove(tagId);
        return HuasenResponse.success(new HashMap<>(), "删除标签成功");
    }

    /**
     * 检查管理员权限
     * 要求JWT code >= 2 (管理员级别)
     */
    private ResponseEntity<HuasenResponse> checkAdminPermission(HttpServletRequest request) {
        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 2) {
            return HuasenResponse.forbidden("权限不足");
        }
        return null;
    }
}

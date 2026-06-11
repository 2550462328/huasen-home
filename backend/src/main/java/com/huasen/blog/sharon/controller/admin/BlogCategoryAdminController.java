package com.huasen.blog.sharon.controller.admin;

import com.huasen.blog.sharon.entity.BlogCategory;
import com.huasen.blog.sharon.service.BlogCategoryAdminService;
import com.huasen.common.dto.HuasenResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 博客分类后台管理控制器
 * 路径: /api/blog-sharon/admin/categories
 * 需要管理员JWT认证(code >= 2)
 */
@RestController
@RequestMapping("/blog-sharon/admin/categories")
public class BlogCategoryAdminController {

    @Autowired
    private BlogCategoryAdminService blogCategoryAdminService;

    /**
     * 所有分类(树形结构)
     * GET /api/blog-sharon/admin/categories
     */
    @GetMapping
    public ResponseEntity<HuasenResponse> list(HttpServletRequest request) {
        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        List<Map<String, Object>> tree = blogCategoryAdminService.findAll();
        return HuasenResponse.success(tree, "查询分类列表成功");
    }

    /**
     * 创建分类
     * POST /api/blog-sharon/admin/categories
     */
    @PostMapping
    public ResponseEntity<HuasenResponse> create(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        BlogCategory category = blogCategoryAdminService.save(params);
        return HuasenResponse.success(category, "创建分类成功");
    }

    /**
     * 更新分类
     * PUT /api/blog-sharon/admin/categories/{cateId}
     */
    @PutMapping("/{cateId}")
    public ResponseEntity<HuasenResponse> update(
            @PathVariable Long cateId,
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        BlogCategory category = blogCategoryAdminService.update(cateId, params);
        return HuasenResponse.success(category, "更新分类成功");
    }

    /**
     * 删除分类
     * DELETE /api/blog-sharon/admin/categories/{cateId}
     */
    @DeleteMapping("/{cateId}")
    public ResponseEntity<HuasenResponse> delete(
            @PathVariable Long cateId,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        blogCategoryAdminService.remove(cateId);
        return HuasenResponse.success(new HashMap<>(), "删除分类成功");
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

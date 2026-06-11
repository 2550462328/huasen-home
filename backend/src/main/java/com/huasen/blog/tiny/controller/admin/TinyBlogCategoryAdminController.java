package com.huasen.blog.tiny.controller.admin;

import com.huasen.blog.tiny.entity.TinyBlogCategory;
import com.huasen.blog.tiny.service.TinyBlogCategoryAdminService;
import com.huasen.common.dto.HuasenResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny Blog 分类后台管理控制器
 * 路径: /api/tiny-blog/admin/categories
 * 需要管理员JWT认证(code >= 2)
 */
@RestController
@RequestMapping("/tiny-blog/admin/categories")
public class TinyBlogCategoryAdminController {

    @Autowired
    private TinyBlogCategoryAdminService tinyBlogCategoryAdminService;

    /**
     * 所有分类列表
     * GET /api/tiny-blog/admin/categories
     */
    @GetMapping
    public ResponseEntity<HuasenResponse> list(HttpServletRequest request) {
        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        List<TinyBlogCategory> categories = tinyBlogCategoryAdminService.findAll();
        return HuasenResponse.success(categories, "查询分类列表成功");
    }

    /**
     * 创建分类
     * POST /api/tiny-blog/admin/categories
     */
    @PostMapping
    public ResponseEntity<HuasenResponse> create(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        TinyBlogCategory category = tinyBlogCategoryAdminService.save(params);
        return HuasenResponse.success(category, "创建分类成功");
    }

    /**
     * 更新分类
     * PUT /api/tiny-blog/admin/categories/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<HuasenResponse> update(
            @PathVariable Long id,
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        TinyBlogCategory category = tinyBlogCategoryAdminService.update(id, params);
        return HuasenResponse.success(category, "更新分类成功");
    }

    /**
     * 删除分类(仅当postCount为0时允许)
     * DELETE /api/tiny-blog/admin/categories/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<HuasenResponse> delete(
            @PathVariable Long id,
            HttpServletRequest request) {

        ResponseEntity<HuasenResponse> authCheck = checkAdminPermission(request);
        if (authCheck != null) return authCheck;

        tinyBlogCategoryAdminService.delete(id);
        return HuasenResponse.success(new HashMap<>(), "删除分类成功");
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

package com.huasen.blog.tiny.controller;

import com.huasen.blog.tiny.entity.TinyBlogCategory;
import com.huasen.blog.tiny.service.TinyBlogCategoryService;
import com.huasen.common.dto.HuasenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tiny Blog 分类前台控制器
 * 公开访问,无需认证(D-15)
 * 路径前缀: /api/tiny-blog/categories
 */
@RestController
@RequestMapping("/tiny-blog/categories")
public class TinyBlogCategoryController {

    @Autowired
    private TinyBlogCategoryService tinyBlogCategoryService;

    /**
     * 所有分类列表(按文章数量降序)
     * GET /api/tiny-blog/categories
     */
    @GetMapping
    public ResponseEntity<HuasenResponse> findAll() {
        List<TinyBlogCategory> categories = tinyBlogCategoryService.findAll();
        return HuasenResponse.success(categories, "查询分类列表成功");
    }
}

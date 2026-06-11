package com.huasen.blog.sharon.controller;

import com.huasen.blog.sharon.entity.BlogPost;
import com.huasen.blog.sharon.service.BlogSearchService;
import com.huasen.common.dto.HuasenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 博客搜索控制器
 * 提供ES全文搜索接口
 * D-12: ES不可用时返回"搜索服务不可用"
 */
@RestController
@RequestMapping("/blog-sharon/posts")
public class BlogSearchController {

    @Autowired
    private BlogSearchService blogSearchService;

    /**
     * 全文搜索文章
     * GET /api/blog-sharon/posts/search/{page}?keyword=xxx
     * @param page 页码
     * @param keyword 搜索关键词
     * @return 搜索结果或不可用提示
     */
    @GetMapping("/search/{page}")
    public ResponseEntity<HuasenResponse> search(
            @PathVariable int page,
            @RequestParam(required = false, defaultValue = "") String keyword) {

        // D-12: ES不可用时返回"搜索服务不可用"
        if (!blogSearchService.isAvailable()) {
            return HuasenResponse.error("搜索服务不可用");
        }

        if (keyword.isBlank()) {
            return HuasenResponse.error("搜索关键词不能为空");
        }

        Page<BlogPost> result = blogSearchService.search(keyword, page, 10);

        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getContent());
        data.put("total", result.getTotalElements());
        data.put("pages", result.getTotalPages());
        data.put("pageNo", page);
        data.put("pageSize", 10);

        return HuasenResponse.success(data, "搜索完成");
    }
}

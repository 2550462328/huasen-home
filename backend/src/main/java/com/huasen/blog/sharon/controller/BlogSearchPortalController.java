package com.huasen.blog.sharon.controller;

import com.huasen.blog.sharon.dto.BlogPostVO;
import com.huasen.blog.sharon.entity.BlogPost;
import com.huasen.blog.sharon.mapper.BlogPostMapper;
import com.huasen.blog.sharon.service.BlogSearchService;
import com.huasen.common.dto.HuasenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 博客搜索前台控制器(扁平路径)
 * 匹配 portal BlogList.vue 调用的 GET /api/blog-sharon/search?keyword=&size=
 * - ES 不可用时返回错误(前端据此关闭搜索框, D-12)
 * - keyword 为空时返回空结果成功(供前端探测 ES 是否可用)
 * 返回 {content, totalElements, currentPage} 结构,匹配前端读取字段
 */
@RestController
@RequestMapping("/blog-sharon/search")
public class BlogSearchPortalController {

    @Autowired
    private BlogSearchService blogSearchService;

    @Autowired
    private BlogPostMapper blogPostMapper;

    @GetMapping
    public ResponseEntity<HuasenResponse> search(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int size) {

        // D-12: ES 不可用时返回错误,前端禁用搜索框
        if (!blogSearchService.isAvailable()) {
            return HuasenResponse.error("搜索服务不可用");
        }

        Map<String, Object> data = new HashMap<>();
        // 探测请求(空关键词): 返回空结果成功,表明 ES 可用
        if (keyword.isBlank()) {
            data.put("content", List.of());
            data.put("totalElements", 0L);
            data.put("totalPages", 0);
            data.put("currentPage", page);
            return HuasenResponse.success(data, "搜索服务可用");
        }

        Page<BlogPost> result = blogSearchService.search(keyword, page, size);
        List<BlogPostVO> voList = blogPostMapper.toVOList(result.getContent());

        data.put("content", voList);
        data.put("totalElements", result.getTotalElements());
        data.put("totalPages", result.getTotalPages());
        data.put("currentPage", page);
        return HuasenResponse.success(data, "搜索完成");
    }
}

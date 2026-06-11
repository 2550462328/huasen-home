package com.huasen.blog.sharon.controller;

import com.huasen.blog.sharon.dto.BlogPostVO;
import com.huasen.blog.sharon.entity.BlogCategory;
import com.huasen.blog.sharon.entity.BlogPost;
import com.huasen.blog.sharon.mapper.BlogPostMapper;
import com.huasen.blog.sharon.service.BlogCategoryService;
import com.huasen.blog.sharon.service.BlogPostService;
import com.huasen.common.dto.HuasenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 博客分类前台控制器
 * 公开访问,无需认证(D-09)
 * 路径前缀: /api/blog-sharon/categories (D-07)
 */
@RestController
@RequestMapping("/blog-sharon/categories")
public class BlogCategoryController {

    @Autowired
    private BlogCategoryService blogCategoryService;

    @Autowired
    private BlogPostService blogPostService;

    @Autowired
    private BlogPostMapper blogPostMapper;

    /**
     * 全部分类列表(扁平,供前台筛选栏使用)
     * GET /api/blog-sharon/categories
     * 返回 {id, name} 结构,匹配前端 BlogList.vue 的 cat.id / cat.name
     */
    /**
     * 一级分类列表 (仅根节点, 供前台初始加载)
     * GET /api/blog-sharon/categories
     */
    @GetMapping
    public ResponseEntity<HuasenResponse> findRootCategories() {
        List<BlogCategory> roots = blogCategoryService.findRootCategories();
        Map<Long, Long> countMap = blogCategoryService.getPostCountMap();

        List<Map<String, Object>> result = new ArrayList<>();
        for (BlogCategory category : roots) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", category.getCateId());
            item.put("name", category.getCateName());
            item.put("url", category.getCateUrl());
            item.put("icon", category.getCateIcon());
            item.put("desc", category.getCateDesc());
            item.put("count", countMap.getOrDefault(category.getCateId(), 0L));
            item.put("parentId", 0);
            item.put("hasChildren", category.getHasChild() != null && category.getHasChild());
            result.add(item);
        }
        return HuasenResponse.success(result, "查询分类列表成功");
    }

    /**
     * 全部分类 (扁平全量, 供前台一次性构建分类树并缓存)
     * GET /api/blog-sharon/categories/tree
     * 一次返回所有层级分类, 前端自行组装父子结构, 避免逐级懒加载延迟
     */
    @GetMapping("/tree")
    public ResponseEntity<HuasenResponse> findAllCategoriesFlat() {
        List<BlogCategory> all = blogCategoryService.findAllCategories();
        Map<Long, Long> countMap = blogCategoryService.getPostCountMap();

        // 统计每个分类是否有子分类
        java.util.Set<Long> parentIds = new java.util.HashSet<>();
        for (BlogCategory category : all) {
            Long pid = category.getCatePid();
            if (pid != null && pid != 0) {
                parentIds.add(pid);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (BlogCategory category : all) {
            Long pid = category.getCatePid();
            Map<String, Object> item = new HashMap<>();
            item.put("id", category.getCateId());
            item.put("name", category.getCateName());
            item.put("url", category.getCateUrl());
            item.put("icon", category.getCateIcon());
            item.put("desc", category.getCateDesc());
            item.put("count", countMap.getOrDefault(category.getCateId(), 0L));
            item.put("parentId", (pid == null) ? 0 : pid);
            item.put("hasChildren", parentIds.contains(category.getCateId()));
            result.add(item);
        }
        return HuasenResponse.success(result, "查询全部分类成功");
    }

    /**
     * 子分类列表 (懒加载, 点击展开时请求)
     * GET /api/blog-sharon/categories/{parentId}/children
     */
    @GetMapping("/{parentId}/children")
    public ResponseEntity<HuasenResponse> findChildren(@PathVariable Long parentId) {
        List<BlogCategory> children = blogCategoryService.findByParentId(parentId, 1);
        Map<Long, Long> countMap = blogCategoryService.getPostCountMap();

        List<Map<String, Object>> result = new ArrayList<>();
        for (BlogCategory category : children) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", category.getCateId());
            item.put("name", category.getCateName());
            item.put("url", category.getCateUrl());
            item.put("icon", category.getCateIcon());
            item.put("desc", category.getCateDesc());
            item.put("count", countMap.getOrDefault(category.getCateId(), 0L));
            item.put("parentId", parentId);
            item.put("hasChildren", category.getHasChild() != null && category.getHasChild());
            result.add(item);
        }
        return HuasenResponse.success(result, "查询子分类成功");
    }

    /**
     * 分类列表(含子分类文章数量统计)
     * GET /api/blog-sharon/categories/list/{pId}/{pageNo}
     * @param pId 父分类ID
     * @param pageNo 页码
     */
    @GetMapping("/list/{pId}/{pageNo}")
    public ResponseEntity<HuasenResponse> findByParentId(
            @PathVariable Long pId,
            @PathVariable int pageNo) {

        List<BlogCategory> categories = blogCategoryService.findByParentId(pId, pageNo);
        return HuasenResponse.success(categories, "查询分类列表成功");
    }

    /**
     * 单个分类详情
     * GET /api/blog-sharon/categories/{cateUrl}
     */
    @GetMapping("/{cateUrl}")
    public ResponseEntity<HuasenResponse> findByCateUrl(@PathVariable String cateUrl) {
        Optional<BlogCategory> category = blogCategoryService.findByCateUrl(cateUrl);
        if (category.isEmpty()) {
            return HuasenResponse.error("分类不存在");
        }
        return HuasenResponse.success(category.get(), "查询分类详情成功");
    }

    /**
     * 分类下文章分页
     * GET /api/blog-sharon/categories/{cateUrl}/page/{page}
     */
    @GetMapping("/{cateUrl}/page/{page}")
    public ResponseEntity<HuasenResponse> findPostsByCateUrl(
            @PathVariable String cateUrl,
            @PathVariable int page) {

        Optional<BlogCategory> category = blogCategoryService.findByCateUrl(cateUrl);
        if (category.isEmpty()) {
            return HuasenResponse.error("分类不存在");
        }

        Page<BlogPost> posts = blogPostService.findPostsByCategory(category.get(), page);
        List<BlogPostVO> voList = blogPostMapper.toVOList(posts.getContent());

        Map<String, Object> result = new HashMap<>();
        result.put("content", voList);
        result.put("totalElements", posts.getTotalElements());
        result.put("totalPages", posts.getTotalPages());
        result.put("currentPage", page);
        return HuasenResponse.success(result, "查询分类文章成功");
    }
}

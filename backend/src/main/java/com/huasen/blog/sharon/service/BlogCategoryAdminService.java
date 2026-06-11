package com.huasen.blog.sharon.service;

import com.huasen.blog.sharon.entity.BlogCategory;
import com.huasen.blog.sharon.repository.BlogCategoryRepository;
import com.huasen.blog.sharon.repository.BlogPostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 博客分类后台管理服务
 * 提供分类的树形查询、CRUD操作
 */
@Service
public class BlogCategoryAdminService {

    @Autowired
    private BlogCategoryRepository blogCategoryRepository;

    @Autowired
    private BlogPostRepository blogPostRepository;

    /**
     * 查询所有分类(树形结构)
     * 将平铺的分类列表组装为树形结构返回
     * @return 树形分类列表(顶层分类包含子分类)
     */
    public List<Map<String, Object>> findAll() {
        List<BlogCategory> allCategories = blogCategoryRepository.findAll();

        // 标记是否有子分类
        Set<Long> parentIds = allCategories.stream()
                .map(BlogCategory::getCatePid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 构建树形结构
        List<Map<String, Object>> tree = new ArrayList<>();
        // 获取顶层分类(catePid = 0 或 null)
        List<BlogCategory> roots = allCategories.stream()
                .filter(c -> c.getCatePid() == null || c.getCatePid() == 0)
                .collect(Collectors.toList());

        for (BlogCategory root : roots) {
            Map<String, Object> node = buildCategoryNode(root, allCategories, parentIds);
            tree.add(node);
        }

        return tree;
    }

    /**
     * 创建分类
     * @param params 分类参数(cateName, catePid, cateUrl, cateIcon, cateDesc)
     * @return 保存后的分类实体
     */
    @Transactional
    public BlogCategory save(Map<String, Object> params) {
        BlogCategory category = new BlogCategory();
        category.setCateName((String) params.get("cateName"));
        category.setCatePid(params.get("catePid") != null
                ? ((Number) params.get("catePid")).longValue() : 0L);
        category.setCateUrl((String) params.get("cateUrl"));
        category.setCateIcon((String) params.get("cateIcon"));
        category.setCateDesc((String) params.get("cateDesc"));
        return blogCategoryRepository.save(category);
    }

    /**
     * 更新分类
     * @param cateId 分类ID
     * @param params 更新参数
     * @return 更新后的分类实体
     */
    @Transactional
    public BlogCategory update(Long cateId, Map<String, Object> params) {
        BlogCategory category = blogCategoryRepository.findById(cateId)
                .orElseThrow(() -> new RuntimeException("分类不存在: " + cateId));

        if (params.containsKey("cateName")) {
            category.setCateName((String) params.get("cateName"));
        }
        if (params.containsKey("catePid")) {
            category.setCatePid(((Number) params.get("catePid")).longValue());
        }
        if (params.containsKey("cateUrl")) {
            category.setCateUrl((String) params.get("cateUrl"));
        }
        if (params.containsKey("cateIcon")) {
            category.setCateIcon((String) params.get("cateIcon"));
        }
        if (params.containsKey("cateDesc")) {
            category.setCateDesc((String) params.get("cateDesc"));
        }

        return blogCategoryRepository.save(category);
    }

    /**
     * 删除分类
     * 检查是否有关联文章,有则拒绝删除
     * @param cateId 分类ID
     */
    @Transactional
    public void remove(Long cateId) {
        BlogCategory category = blogCategoryRepository.findById(cateId)
                .orElseThrow(() -> new RuntimeException("分类不存在: " + cateId));

        // 检查是否有关联文章 - 通过反向查询 BlogPostRepository
        long postCount = blogPostRepository.findByCategoriesContaining(category,
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements();
        if (postCount > 0) {
            throw new RuntimeException("该分类下有 " + postCount + " 篇文章,无法删除");
        }

        // 检查是否有子分类
        List<BlogCategory> children = blogCategoryRepository.findAllByCatePid(cateId);
        if (!children.isEmpty()) {
            throw new RuntimeException("该分类下有 " + children.size() + " 个子分类,无法删除");
        }

        blogCategoryRepository.delete(category);
    }

    /**
     * 递归构建分类树节点
     */
    private Map<String, Object> buildCategoryNode(BlogCategory category,
                                                   List<BlogCategory> allCategories,
                                                   Set<Long> parentIds) {
        Map<String, Object> node = new HashMap<>();
        node.put("cateId", category.getCateId());
        node.put("catePid", category.getCatePid());
        node.put("cateName", category.getCateName());
        node.put("cateUrl", category.getCateUrl());
        node.put("cateIcon", category.getCateIcon());
        node.put("cateDesc", category.getCateDesc());
        node.put("hasChild", parentIds.contains(category.getCateId()));

        // 查找子分类
        List<BlogCategory> children = allCategories.stream()
                .filter(c -> Objects.equals(c.getCatePid(), category.getCateId()))
                .collect(Collectors.toList());

        if (!children.isEmpty()) {
            List<Map<String, Object>> childNodes = new ArrayList<>();
            for (BlogCategory child : children) {
                childNodes.add(buildCategoryNode(child, allCategories, parentIds));
            }
            node.put("children", childNodes);
        }

        return node;
    }
}

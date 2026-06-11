package com.huasen.blog.sharon.service;

import com.huasen.blog.sharon.entity.BlogCategory;
import com.huasen.blog.sharon.repository.BlogCategoryRepository;
import com.huasen.blog.sharon.repository.BlogPostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 博客分类前台服务
 * 提供分类列表查询和分类详情
 */
@Service
public class BlogCategoryService {

    @Autowired
    private BlogCategoryRepository blogCategoryRepository;

    @Autowired
    private BlogPostRepository blogPostRepository;

    /**
     * 查询根分类 (cate_pid = 0 或 NULL), 即一级节点
     */
    public List<BlogCategory> findRootCategories() {
        List<BlogCategory> roots = blogCategoryRepository.findRootCategories();
        for (BlogCategory category : roots) {
            List<BlogCategory> children = blogCategoryRepository.findAllByCatePid(category.getCateId());
            category.setHasChild(!children.isEmpty());
        }
        return roots;
    }

    /**
     * 查询子分类列表,附带每个分类的文章数量
     * @param pid 父分类ID
     * @param page 页码(保留参数,分类通常不分页)
     */
    public List<BlogCategory> findByParentId(Long pid, int page) {
        List<BlogCategory> categories = blogCategoryRepository.findAllByCatePid(pid);
        // 为每个分类计算文章数量并设置hasChild
        for (BlogCategory category : categories) {
            long postCount = blogPostRepository.findByCategoriesContaining(
                    category, PageRequest.of(0, 1)).getTotalElements();
            // 使用transient字段标记是否有子分类
            List<BlogCategory> children = blogCategoryRepository.findAllByCatePid(category.getCateId());
            category.setHasChild(!children.isEmpty());
        }
        return categories;
    }

    /**
     * 查询全部分类 (扁平, 不分父子), 供前台一次性构建分类树
     */
    public List<BlogCategory> findAllCategories() {
        return blogCategoryRepository.findAll();
    }

    /**
     * 一次性查询每个分类的已发布文章数(直属计数)
     * @return cateId -> count 的映射
     */
    public Map<Long, Long> getPostCountMap() {
        Map<Long, Long> countMap = new HashMap<>();
        for (Object[] row : blogPostRepository.countPublishedPostsGroupByCategory()) {
            Long cateId = ((Number) row[0]).longValue();
            Long count = ((Number) row[1]).longValue();
            countMap.put(cateId, count);
        }
        return countMap;
    }

    /**
     * 按URL查询单个分类详情
     */
    public Optional<BlogCategory> findByCateUrl(String cateUrl) {
        return blogCategoryRepository.findByCateUrl(cateUrl);
    }
}

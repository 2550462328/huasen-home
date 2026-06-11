package com.huasen.blog.tiny.service;

import com.huasen.blog.tiny.entity.TinyBlogCategory;
import com.huasen.blog.tiny.repository.TinyBlogCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tiny Blog 分类后台管理服务
 * 提供分类CRUD操作
 */
@Service
public class TinyBlogCategoryAdminService {

    @Autowired
    private TinyBlogCategoryRepository tinyBlogCategoryRepository;

    /**
     * 查询所有分类
     * @return 分类列表
     */
    public List<TinyBlogCategory> findAll() {
        return tinyBlogCategoryRepository.findAll();
    }

    /**
     * 创建分类
     * @param params 包含name字段
     * @return 保存后的分类
     */
    public TinyBlogCategory save(Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("分类名称不能为空");
        }

        // 检查名称唯一性
        Optional<TinyBlogCategory> existing = tinyBlogCategoryRepository.findByName(name.trim());
        if (existing.isPresent()) {
            throw new RuntimeException("分类名称已存在: " + name);
        }

        TinyBlogCategory category = new TinyBlogCategory();
        category.setName(name.trim());
        category.setPostCount(0);
        return tinyBlogCategoryRepository.save(category);
    }

    /**
     * 更新分类名称
     * @param id 分类ID
     * @param params 包含name字段
     * @return 更新后的分类
     */
    public TinyBlogCategory update(Long id, Map<String, Object> params) {
        TinyBlogCategory category = tinyBlogCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("分类不存在: " + id));

        if (params.containsKey("name")) {
            String name = (String) params.get("name");
            if (name != null && !name.trim().isEmpty()) {
                // 检查名称唯一性(排除自身)
                Optional<TinyBlogCategory> existing = tinyBlogCategoryRepository.findByName(name.trim());
                if (existing.isPresent() && !existing.get().getId().equals(id)) {
                    throw new RuntimeException("分类名称已存在: " + name);
                }
                category.setName(name.trim());
            }
        }

        return tinyBlogCategoryRepository.save(category);
    }

    /**
     * 删除分类(仅当postCount为0时允许)
     * @param id 分类ID
     */
    @Transactional
    public void delete(Long id) {
        TinyBlogCategory category = tinyBlogCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("分类不存在: " + id));

        if (category.getPostCount() != null && category.getPostCount() > 0) {
            throw new RuntimeException("该分类下还有文章,无法删除");
        }

        tinyBlogCategoryRepository.delete(category);
    }
}

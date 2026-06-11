package com.huasen.blog.tiny.service;

import com.huasen.blog.tiny.entity.TinyBlogCategory;
import com.huasen.blog.tiny.repository.TinyBlogCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Tiny Blog 分类前台服务
 * 提供分类列表查询(按文章数量降序)
 */
@Service
public class TinyBlogCategoryService {

    @Autowired
    private TinyBlogCategoryRepository tinyBlogCategoryRepository;

    /**
     * 查询所有分类,按文章数量降序排列
     * @return 分类列表
     */
    public List<TinyBlogCategory> findAll() {
        return tinyBlogCategoryRepository.findAll(
                Sort.by(Sort.Direction.DESC, "postCount"));
    }

    /**
     * 按ID查询分类
     * @param id 分类ID
     * @return 分类详情
     */
    public Optional<TinyBlogCategory> findById(Long id) {
        return tinyBlogCategoryRepository.findById(id);
    }
}

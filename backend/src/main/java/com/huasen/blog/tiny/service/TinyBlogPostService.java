package com.huasen.blog.tiny.service;

import com.huasen.blog.tiny.entity.TinyBlogPost;
import com.huasen.blog.tiny.repository.TinyBlogPostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Tiny Blog 文章前台服务
 * 提供文章分页查询和详情(含访问量递增)
 */
@Service
public class TinyBlogPostService {

    private static final int DEFAULT_PAGE_SIZE = 10;

    @Autowired
    private TinyBlogPostRepository tinyBlogPostRepository;

    /**
     * 分页查询文章列表
     * @param page 页码(从1开始)
     * @param size 每页条数
     * @param categoryId 分类ID(可选,null表示查全部)
     * @param keyword 标题关键词(可选,非空时按标题模糊检索,优先级高于分类)
     * @return 分页结果
     */
    public Page<TinyBlogPost> findByPage(int page, int size, Long categoryId, String keyword) {
        int pageSize = size > 0 ? size : DEFAULT_PAGE_SIZE;
        Pageable pageable = PageRequest.of(
                Math.max(0, page - 1), pageSize,
                Sort.by(Sort.Direction.DESC, "publishDate"));

        if (keyword != null && !keyword.trim().isEmpty()) {
            return tinyBlogPostRepository.findByTitleContaining(keyword.trim(), pageable);
        }
        if (categoryId != null) {
            return tinyBlogPostRepository.findByCategoryId(categoryId, pageable);
        }
        return tinyBlogPostRepository.findAll(pageable);
    }

    /**
     * 查询文章详情并递增访问量
     * 使用@Transactional确保@Modifying的incrementVisitCount正确执行
     * @param id 文章ID
     * @return 文章详情
     */
    @Transactional
    public Optional<TinyBlogPost> findById(Long id) {
        tinyBlogPostRepository.incrementVisitCount(id);
        return tinyBlogPostRepository.findById(id);
    }
}

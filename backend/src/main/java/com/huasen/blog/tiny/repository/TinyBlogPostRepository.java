package com.huasen.blog.tiny.repository;

import com.huasen.blog.tiny.entity.TinyBlogPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Tiny Blog 文章仓库
 */
@Repository
public interface TinyBlogPostRepository extends JpaRepository<TinyBlogPost, Long> {

    /**
     * 按分类ID分页查询文章
     */
    Page<TinyBlogPost> findByCategoryId(Long categoryId, Pageable pageable);

    /**
     * 按标题关键词搜索文章
     */
    Page<TinyBlogPost> findByTitleContaining(String keyword, Pageable pageable);

    /**
     * 递增文章访问量
     */
    @Modifying
    @Query("UPDATE TinyBlogPost p SET p.visitCount = p.visitCount + 1 WHERE p.id = :id")
    void incrementVisitCount(@Param("id") Long id);

    /**
     * 查询访问量 Top10 文章 (Phase 13, Plan 04)
     * 用于管理后台数据表盘的热门文章排行
     */
    List<TinyBlogPost> findTop10ByOrderByVisitCountDesc();
}

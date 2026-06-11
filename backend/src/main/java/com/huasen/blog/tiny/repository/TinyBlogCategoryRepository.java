package com.huasen.blog.tiny.repository;

import com.huasen.blog.tiny.entity.TinyBlogCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Tiny Blog 分类仓库
 */
@Repository
public interface TinyBlogCategoryRepository extends JpaRepository<TinyBlogCategory, Long> {

    /**
     * 按分类名称查询
     */
    Optional<TinyBlogCategory> findByName(String name);

    /**
     * 递增分类文章数
     */
    @Modifying
    @Query("UPDATE TinyBlogCategory c SET c.postCount = c.postCount + 1 WHERE c.id = :id")
    void incrementPostCount(@Param("id") Long id);

    /**
     * 递减分类文章数
     */
    @Modifying
    @Query("UPDATE TinyBlogCategory c SET c.postCount = c.postCount - 1 WHERE c.id = :id AND c.postCount > 0")
    void decrementPostCount(@Param("id") Long id);
}

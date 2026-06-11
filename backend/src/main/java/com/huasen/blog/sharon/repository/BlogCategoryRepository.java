package com.huasen.blog.sharon.repository;

import com.huasen.blog.sharon.entity.BlogCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 博客分类仓库
 */
@Repository
public interface BlogCategoryRepository extends JpaRepository<BlogCategory, Long> {

    /**
     * 按父分类ID查询子分类
     */
    List<BlogCategory> findAllByCatePid(Long pid);

    /**
     * 查询根分类 (cate_pid = 0 或 NULL)
     */
    @Query("SELECT c FROM BlogCategory c WHERE c.catePid IS NULL OR c.catePid = 0")
    List<BlogCategory> findRootCategories();

    /**
     * 按分类URL查询
     */
    Optional<BlogCategory> findByCateUrl(String cateUrl);
}

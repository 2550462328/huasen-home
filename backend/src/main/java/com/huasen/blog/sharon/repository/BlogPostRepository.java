package com.huasen.blog.sharon.repository;

import com.huasen.blog.sharon.entity.BlogCategory;
import com.huasen.blog.sharon.entity.BlogPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 博客文章仓库
 */
@Repository
public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {

    /**
     * 按状态和类型分页查询文章
     */
    Page<BlogPost> findByPostStatusAndPostType(Integer status, String type, Pageable pageable);

    /**
     * 按ID查询文章
     */
    Optional<BlogPost> findByPostId(Long postId);

    /**
     * 按标题关键词搜索已发布文章
     */
    Page<BlogPost> findByPostTitleContainingAndPostStatusAndPostType(
            String keyword, Integer status, String type, Pageable pageable);

    /**
     * 按分类查询文章
     */
    Page<BlogPost> findByCategoriesContaining(BlogCategory category, Pageable pageable);

    /**
     * 批量统计每个分类的已发布文章数(直属计数)
     * 一次查询返回 [cateId, count]，避免逐分类查询的 N+1 问题
     */
    @Query("SELECT c.cateId, COUNT(DISTINCT p) FROM BlogPost p JOIN p.categories c " +
            "WHERE p.postStatus = 0 AND p.postType = 'post' GROUP BY c.cateId")
    List<Object[]> countPublishedPostsGroupByCategory();

    /**
     * 按多个分类ID查询已发布文章(分类及其所有子分类)
     * DISTINCT 去重: 一篇文章可能同时属于传入的多个分类
     */
    @Query("SELECT DISTINCT p FROM BlogPost p JOIN p.categories c " +
            "WHERE c.cateId IN :categoryIds AND p.postStatus = :status AND p.postType = :type")
    Page<BlogPost> findByCategoryIdsAndStatusAndType(
            @Param("categoryIds") List<Long> categoryIds,
            @Param("status") Integer status,
            @Param("type") String type,
            Pageable pageable);

    /**
     * 递增文章访问量
     */
    @Modifying
    @Query("UPDATE BlogPost p SET p.postViews = p.postViews + 1 WHERE p.postId = :id")
    void incrementViews(@Param("id") Long id);

    /**
     * 按年份归档统计
     */
    @Query(value = "SELECT YEAR(p.postDate) as yr, COUNT(p) as cnt " +
            "FROM BlogPost p WHERE p.postStatus = 0 AND p.postType = 'post' " +
            "GROUP BY YEAR(p.postDate) ORDER BY yr DESC")
    List<Object[]> findArchiveGroupByYear();

    /**
     * 按年月归档统计
     */
    @Query(value = "SELECT YEAR(p.postDate) as yr, MONTH(p.postDate) as mo, COUNT(p) as cnt " +
            "FROM BlogPost p WHERE p.postStatus = 0 AND p.postType = 'post' " +
            "GROUP BY YEAR(p.postDate), MONTH(p.postDate) ORDER BY yr DESC, mo DESC")
    List<Object[]> findArchiveGroupByYearAndMonth();

    /**
     * 按年份查询文章列表
     */
    @Query("SELECT p FROM BlogPost p WHERE p.postStatus = 0 AND p.postType = 'post' " +
            "AND YEAR(p.postDate) = :year ORDER BY p.postDate DESC")
    List<BlogPost> findByYear(@Param("year") int year);

    /**
     * 按年月查询文章列表
     */
    @Query("SELECT p FROM BlogPost p WHERE p.postStatus = 0 AND p.postType = 'post' " +
            "AND YEAR(p.postDate) = :year AND MONTH(p.postDate) = :month ORDER BY p.postDate DESC")
    List<BlogPost> findByYearAndMonth(@Param("year") int year, @Param("month") int month);

    /**
     * 后台: 按状态分页查询所有文章
     */
    Page<BlogPost> findByPostStatus(Integer status, Pageable pageable);

    /**
     * 后台: 按标题关键词搜索(不限状态)
     */
    Page<BlogPost> findByPostTitleContaining(String keyword, Pageable pageable);

    /**
     * 后台: 按标题关键词+状态搜索
     */
    Page<BlogPost> findByPostTitleContainingAndPostStatus(String keyword, Integer status, Pageable pageable);

    /**
     * 后台: 按分类+状态查询
     */
    Page<BlogPost> findByCategoriesContainingAndPostStatus(BlogCategory category, Integer status, Pageable pageable);

    /**
     * 后台: 按标题关键词+分类+状态搜索
     */
    @Query("SELECT p FROM BlogPost p JOIN p.categories c WHERE p.postTitle LIKE %:keyword% " +
            "AND c.cateId = :categoryId AND p.postStatus = :status")
    Page<BlogPost> searchByKeywordAndCategoryAndStatus(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("status") Integer status,
            Pageable pageable);

    /**
     * 后台: 按标题关键词+分类搜索(不限状态)
     */
    @Query("SELECT p FROM BlogPost p JOIN p.categories c WHERE p.postTitle LIKE %:keyword% " +
            "AND c.cateId = :categoryId")
    Page<BlogPost> searchByKeywordAndCategory(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            Pageable pageable);

    /**
     * 查询访问量 Top10 文章 (Phase 13, Plan 04)
     * 用于管理后台数据表盘的热门文章排行
     */
    List<BlogPost> findTop10ByOrderByPostViewsDesc();
}

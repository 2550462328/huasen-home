package com.huasen.blog.sharon.service;

import com.huasen.blog.sharon.dto.ArchiveDTO;
import com.huasen.blog.sharon.entity.BlogCategory;
import com.huasen.blog.sharon.entity.BlogPost;
import com.huasen.blog.sharon.repository.BlogPostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 博客文章前台服务
 * 提供文章分页、详情、分类/标签筛选、归档等公开查询
 */
@Service
public class BlogPostService {

    private static final int PAGE_SIZE = 10;

    @Autowired
    private BlogPostRepository blogPostRepository;

    @Autowired
    private com.huasen.blog.sharon.repository.BlogCategoryRepository blogCategoryRepository;

    /**
     * 分页查询已发布文章
     * @param page 页码(从1开始)
     * @param tab 排序方式: 0=oldest(ASC postId), 1=newest(DESC postDate), 2=hottest(DESC postViews)
     */
    public Page<BlogPost> findPublishedPosts(int page, int tab) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), PAGE_SIZE, buildSort(tab));
        return blogPostRepository.findByPostStatusAndPostType(0, "post", pageable);
    }

    /**
     * 分页查询某分类(含所有子分类)下的已发布文章
     * @param categoryId 分类ID
     * @param page 页码(从1开始)
     * @param tab 排序方式
     */
    public Page<BlogPost> findPublishedPostsByCategory(Long categoryId, int page, int tab) {
        List<Long> categoryIds = collectCategoryIds(categoryId);
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), PAGE_SIZE, buildSort(tab));
        return blogPostRepository.findByCategoryIdsAndStatusAndType(categoryIds, 0, "post", pageable);
    }

    /**
     * 收集分类ID及其所有后代分类ID(广度优先遍历)
     */
    private List<Long> collectCategoryIds(Long rootId) {
        List<Long> result = new ArrayList<>();
        java.util.Deque<Long> queue = new java.util.ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (result.contains(current)) {
                continue; // 防御环形引用
            }
            result.add(current);
            for (BlogCategory child : blogCategoryRepository.findAllByCatePid(current)) {
                queue.add(child.getCateId());
            }
        }
        return result;
    }

    /**
     * 根据 tab 构建排序规则
     */
    private Sort buildSort(int tab) {
        return switch (tab) {
            case 0 -> Sort.by(Sort.Direction.ASC, "postId");
            case 2 -> Sort.by(Sort.Direction.DESC, "postViews");
            default -> Sort.by(Sort.Direction.DESC, "postDate");
        };
    }

    /**
     * 查询文章详情并递增访问量
     */
    @Transactional
    public Optional<BlogPost> findPostById(Long postId) {
        blogPostRepository.incrementViews(postId);
        return blogPostRepository.findByPostId(postId);
    }

    /**
     * 分类下文章分页
     */
    public Page<BlogPost> findPostsByCategory(BlogCategory category, int page) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "postDate"));
        return blogPostRepository.findByCategoriesContaining(category, pageable);
    }

    /**
     * 标签下文章分页(已废弃 - tag 数据未迁移,返回空页)
     */
    public Page<BlogPost> findPostsByTag(Object tag, int page) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "postDate"));
        return Page.empty(pageable);
    }

    /**
     * 按年归档
     * @return 按年分组的文章列表
     */
    public List<ArchiveDTO> findArchivesByYear() {
        List<Object[]> stats = blogPostRepository.findArchiveGroupByYear();
        List<ArchiveDTO> archives = new ArrayList<>();
        for (Object[] row : stats) {
            int year = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            List<BlogPost> posts = blogPostRepository.findByYear(year);
            archives.add(new ArchiveDTO(String.valueOf(year), null, count, posts));
        }
        return archives;
    }

    /**
     * 按年月归档
     * @return 按年月分组的文章列表
     */
    public List<ArchiveDTO> findArchivesByYearAndMonth() {
        List<Object[]> stats = blogPostRepository.findArchiveGroupByYearAndMonth();
        List<ArchiveDTO> archives = new ArrayList<>();
        for (Object[] row : stats) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            long count = ((Number) row[2]).longValue();
            List<BlogPost> posts = blogPostRepository.findByYearAndMonth(year, month);
            archives.add(new ArchiveDTO(
                    String.valueOf(year),
                    String.valueOf(month),
                    count,
                    posts
            ));
        }
        return archives;
    }
}

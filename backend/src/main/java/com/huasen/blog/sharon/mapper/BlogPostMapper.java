package com.huasen.blog.sharon.mapper;

import com.huasen.blog.sharon.dto.BlogPostVO;
import com.huasen.blog.sharon.dto.BlogPostDetailVO;
import com.huasen.blog.sharon.entity.BlogCategory;
import com.huasen.blog.sharon.entity.BlogPost;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * BlogPost -> BlogPostVO 映射器
 * 手动映射字段,处理 Set<BlogCategory> -> CategoryVO 转换
 */
@Component
public class BlogPostMapper {

    /**
     * 单个实体转VO
     */
    public BlogPostVO toVO(BlogPost post) {
        if (post == null) {
            return null;
        }

        BlogPostVO vo = new BlogPostVO();
        vo.setId(post.getPostId());
        vo.setTitle(post.getPostTitle());
        vo.setCoverImage(post.getPostThumbnail());
        vo.setSummary(post.getPostSummary());
        vo.setPublishDate(post.getPostDate());
        vo.setVisitCount(post.getPostViews());

        // 提取第一个分类,处理空集合
        if (post.getCategories() != null && !post.getCategories().isEmpty()) {
            BlogCategory firstCategory = post.getCategories().iterator().next();
            BlogPostVO.CategoryVO categoryVO = new BlogPostVO.CategoryVO();
            categoryVO.setId(firstCategory.getCateId());
            categoryVO.setName(firstCategory.getCateName());
            categoryVO.setUrl(firstCategory.getCateUrl());
            vo.setCategory(categoryVO);
        } else {
            // 无分类时返回默认占位
            BlogPostVO.CategoryVO defaultCategory = new BlogPostVO.CategoryVO();
            defaultCategory.setId(0L);
            defaultCategory.setName("未分类");
            defaultCategory.setUrl("");
            vo.setCategory(defaultCategory);
        }

        return vo;
    }

    /**
     * 批量转换
     */
    public List<BlogPostVO> toVOList(List<BlogPost> posts) {
        if (posts == null) {
            return List.of();
        }
        return posts.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 实体转详情VO (供详情页使用)
     * 含正文内容, 自动判断 markdown / html 格式
     */
    public BlogPostDetailVO toDetailVO(BlogPost post) {
        if (post == null) {
            return null;
        }

        BlogPostDetailVO vo = new BlogPostDetailVO();
        vo.setId(post.getPostId());
        vo.setTitle(post.getPostTitle());
        vo.setCoverImage(post.getPostThumbnail());
        vo.setSummary(post.getPostSummary());
        vo.setPublishDate(post.getPostDate());
        vo.setVisitCount(post.getPostViews());

        // 正文: 优先 markdown 源文, 否则渲染好的 HTML
        if (post.getPostContentMd() != null && !post.getPostContentMd().isBlank()) {
            vo.setContent(post.getPostContentMd());
            vo.setFormatContent("markdown");
        } else {
            vo.setContent(post.getPostContent());
            vo.setFormatContent("html");
        }

        // 分类: 取第一个
        if (post.getCategories() != null && !post.getCategories().isEmpty()) {
            BlogCategory firstCategory = post.getCategories().iterator().next();
            BlogPostVO.CategoryVO categoryVO = new BlogPostVO.CategoryVO();
            categoryVO.setId(firstCategory.getCateId());
            categoryVO.setName(firstCategory.getCateName());
            categoryVO.setUrl(firstCategory.getCateUrl());
            vo.setCategory(categoryVO);
        }

        return vo;
    }
}

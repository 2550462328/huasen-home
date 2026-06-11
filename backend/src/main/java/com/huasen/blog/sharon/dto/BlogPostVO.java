package com.huasen.blog.sharon.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 博客文章视图对象 - 前台接口专用
 * 字段名匹配前端 BlogList.vue 期望的格式
 */
@Data
public class BlogPostVO {

    private Long id;                  // 映射自 postId
    private String title;             // 映射自 postTitle
    private String coverImage;        // 映射自 postThumbnail
    private String summary;           // 映射自 postSummary

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm", timezone = "Asia/Shanghai")
    private Date publishDate;         // 映射自 postDate

    private Long visitCount;          // 映射自 postViews
    private CategoryVO category;      // 取 categories 的第一个

    /**
     * 分类视图对象
     */
    @Data
    public static class CategoryVO {
        private Long id;              // 映射自 cateId
        private String name;          // 映射自 cateName
        private String url;           // 映射自 cateUrl
    }
}

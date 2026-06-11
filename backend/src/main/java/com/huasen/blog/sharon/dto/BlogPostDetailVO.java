package com.huasen.blog.sharon.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 博客文章详情视图对象 - 前台详情页专用
 * 字段名匹配前端 BlogDetail.vue 期望的格式 (title/content/formatContent/...)
 */
@Data
public class BlogPostDetailVO {

    private Long id;                  // 映射自 postId
    private String title;             // 映射自 postTitle
    private String content;           // markdown 优先, 否则 HTML
    private String formatContent;     // "markdown" 或 "html"
    private String coverImage;        // 映射自 postThumbnail
    private String summary;           // 映射自 postSummary

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm", timezone = "Asia/Shanghai")
    private Date publishDate;         // 映射自 postDate

    private Long visitCount;          // 映射自 postViews
    private BlogPostVO.CategoryVO category;
}

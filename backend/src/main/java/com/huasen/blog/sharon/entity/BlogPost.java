package com.huasen.blog.sharon.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * 博客文章实体
 * 对应blog-sharon源数据库halo_post表
 * 不关联用户(D-04), 不关联评论(D-05)
 */
@Entity
@Table(name = "halo_post", schema = "huasen_portal")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlogPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;

    @Column(name = "post_title", length = 200)
    private String postTitle;

    @Column(name = "post_type", length = 20)
    private String postType = "post";

    @Lob
    @Column(name = "post_content_md", columnDefinition = "LONGTEXT")
    private String postContentMd;

    @Lob
    @Column(name = "post_content", columnDefinition = "LONGTEXT")
    private String postContent;

    @Column(name = "post_url", length = 200, unique = true)
    private String postUrl;

    @Column(name = "post_summary", length = 500)
    private String postSummary;

    @Column(name = "post_thumbnail", length = 500)
    private String postThumbnail;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm", timezone = "Asia/Shanghai")
    @Column(name = "post_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date postDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm", timezone = "Asia/Shanghai")
    @Column(name = "post_update")
    @Temporal(TemporalType.TIMESTAMP)
    private Date postUpdate;

    @Column(name = "post_status")
    private Integer postStatus = 0;

    @Column(name = "post_views")
    private Long postViews = 0L;

    @Column(name = "allow_comment")
    private Integer allowComment;

    @Column(name = "custom_tpl", length = 200)
    private String customTpl;

    @ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinTable(
            name = "halo_posts_categories",
            schema = "huasen_portal",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "cate_id")
    )
    private Set<BlogCategory> categories = new HashSet<>();
}

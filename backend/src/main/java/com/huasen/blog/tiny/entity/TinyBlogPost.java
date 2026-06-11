package com.huasen.blog.tiny.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Tiny Blog 文章实体
 * 对应 huasen_portal 数据库 tb_post 表
 */
@Entity
@Table(name = "tb_post")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TinyBlogPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", length = 255)
    private String title;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Shanghai")
    @Column(name = "publish_date")
    @Temporal(TemporalType.DATE)
    private Date publishDate;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "cover_image", length = 255)
    private String coverImage;

    @Column(name = "author", length = 100)
    private String author;

    @Lob
    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "visit_count")
    private Integer visitCount = 0;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm", timezone = "Asia/Shanghai")
    @Column(name = "create_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm", timezone = "Asia/Shanghai")
    @Column(name = "update_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private TinyBlogCategory category;
}

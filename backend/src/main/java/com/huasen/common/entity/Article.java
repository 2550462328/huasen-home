package com.huasen.common.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章实体
 * 对应Node.js: mongodb/model/article.js
 */
@Entity
@Table(name = "article")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 发布者id */
    @Column(length = 100)
    private String manageId;

    /** 标题 */
    @Column(nullable = false, length = 100)
    private String title;

    /** 权限码(0-3) */
    @Column(nullable = false)
    private Integer code = 0;

    /** 标签 (a/b/c格式) */
    @Column(length = 200)
    private String tag;

    /** 最后修改时间 */
    @Column(length = 20)
    private String time;

    /** 内容 */
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    /** 封面图片 */
    @Column(length = 500)
    private String bannerImg;

    /** 是否草稿 */
    @Column(nullable = false)
    private Boolean isDraft = false;

    /** 是否启用 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 前端沿用 MongoDB 时期的 _id 约定，序列化时额外暴露 _id（等于数值主键 id） */
    @JsonProperty("_id")
    public Long get_id() {
        return id;
    }
}

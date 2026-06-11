package com.huasen.blog.sharon.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 博客分类实体
 * 对应blog-sharon源数据库halo_category表
 */
@Entity
@Table(name = "halo_category", schema = "huasen_portal")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlogCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cate_id")
    private Long cateId;

    @Column(name = "cate_pid")
    private Long catePid;

    @Column(name = "cate_name", length = 100)
    private String cateName;

    @Column(name = "cate_url", length = 200)
    private String cateUrl;

    @Column(name = "cate_icon", length = 200)
    private String cateIcon;

    @Column(name = "cate_desc", length = 500)
    private String cateDesc;

    /** 是否有子分类(非持久化字段) */
    @Transient
    private Boolean hasChild;
}

package com.huasen.blog.tiny.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tiny Blog 分类实体
 * 对应 huasen_portal 数据库 tb_category 表
 */
@Entity
@Table(name = "tb_category")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TinyBlogCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, unique = true)
    private String name;

    @Column(name = "post_count")
    private Integer postCount = 0;
}

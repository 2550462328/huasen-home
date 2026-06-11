package com.huasen.blog.sharon.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 博客标签 — 标签数据未迁移(08-CONTEXT 决策),保留 POJO 仅用于兼容旧前端接口返回结构
 * 不再是 JPA 实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlogTag {

    private Long tagId;

    private String tagName;

    private String tagUrl;
}

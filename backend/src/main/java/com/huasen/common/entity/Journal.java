package com.huasen.common.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订阅源实体
 * 对应Node.js: mongodb/model/journal.js
 */
@Entity
@Table(name = "journal")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Journal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 名称 */
    @Column(length = 50)
    private String name;

    /** 栏目仓库 (JSON数组字符串) */
    @Column(columnDefinition = "TEXT")
    private String columnStore;

    /** 拓展对象 (JSON对象字符串) */
    @Column(columnDefinition = "TEXT")
    private String expand;

    /** 是否可用 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 权限码(0-3) */
    @Column(nullable = false)
    private Integer code = 0;

    /** 前端沿用 MongoDB 时期的 _id 约定，序列化时额外暴露 _id（等于数值主键 id） */
    @JsonProperty("_id")
    public Long get_id() {
        return id;
    }
}

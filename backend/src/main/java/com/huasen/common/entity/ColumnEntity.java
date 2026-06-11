package com.huasen.common.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 栏目实体
 * 类名使用ColumnEntity避免与jakarta.persistence.Column冲突
 * 表名使用column_table避免MySQL保留字冲突
 */
@Entity
@Table(name = "column_table")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ColumnEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mongo_id", unique = true, length = 24)
    private String mongoId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String icon;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private Integer code = 0;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    /** 前端沿用 MongoDB 时期的 _id 约定，序列化时额外暴露 _id（等于数值主键 id） */
    @JsonProperty("_id")
    public Long get_id() {
        return id;
    }
}

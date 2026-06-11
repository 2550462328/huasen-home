package com.huasen.common.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网站链接实体
 */
@Entity
@Table(name = "site")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mongo_id", unique = true, length = 24)
    private String mongoId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String icon;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    /** 拓展对象 (JSON对象字符串)，前端按字符串消费，与 Journal.expand 保持一致 */
    @Column(columnDefinition = "json")
    private String expand;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private Integer code = 0;

    /** 前端沿用 MongoDB 时期的 _id 约定，序列化时额外暴露 _id（等于数值主键 id） */
    @JsonProperty("_id")
    public Long get_id() {
        return id;
    }
}

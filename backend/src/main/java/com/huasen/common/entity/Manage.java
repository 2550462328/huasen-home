package com.huasen.common.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员实体
 */
@Entity
@Table(name = "manage")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Manage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String accountId;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(length = 50)
    private String name;

    @Column(nullable = false)
    private Integer code = 3;

    @Column(nullable = false)
    private Boolean enabled = true;

    /** 前端沿用 MongoDB 时期的 _id 约定，序列化时额外暴露 _id（等于数值主键 id） */
    @JsonProperty("_id")
    public Long get_id() {
        return id;
    }
}

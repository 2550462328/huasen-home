package com.huasen.common.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.util.List;
import java.util.Map;

/**
 * 用户实体
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String accountId;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(length = 50)
    private String name = "花酱";

    @Column(length = 255)
    private String headImg;

    @Column(nullable = false)
    private Integer code = 0;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(length = 10)
    private String time;

    @Type(JsonType.class)
    @Column(columnDefinition = "json")
    private List<Map<String, Object>> records;

    @Type(JsonType.class)
    @Column(columnDefinition = "json")
    private Map<String, Object> config;

    /** 前端沿用 MongoDB 时期的 _id 约定，序列化时额外暴露 _id（等于数值主键 id） */
    @JsonProperty("_id")
    public Long get_id() {
        return id;
    }
}

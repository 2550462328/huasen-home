package com.huasen.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * AI生成调用审计日志
 * 用于记录所有AI调用的输入/输出、质量指标、编辑距离等
 */
@Data
@Entity
@Table(name = "ai_generation_log")
public class AiGenerationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * AI功能类型：tinyblog_summary
     */
    @Column(name = "feature", nullable = false, length = 32)
    private String feature;

    /**
     * 关联实体ID (TinyBlogPost.id)
     */
    @Column(name = "ref_id", length = 64)
    private String refId;

    /**
     * SHA-256 of input content for cache collision detection
     */
    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    /**
     * 使用的模型名称 (e.g., qwen-plus)
     */
    @Column(name = "model", nullable = false, length = 32)
    private String model;

    /**
     * DashScope requestId (for support tickets)
     */
    @Column(name = "request_id", length = 64)
    private String requestId;

    /**
     * Prompt版本 (e.g., v1, bumped on prompt changes)
     */
    @Column(name = "prompt_version", nullable = false, length = 16)
    private String promptVersion;

    /**
     * AI生成的原始输出 (post-sanitize)
     */
    @Column(name = "ai_output", nullable = false, columnDefinition = "TEXT")
    private String aiOutput;

    /**
     * stop | length | error
     */
    @Column(name = "finish_reason", nullable = false, length = 16)
    private String finishReason;

    /**
     * 输入token数量
     */
    @Column(name = "input_tokens")
    private Integer inputTokens;

    /**
     * 输出token数量
     */
    @Column(name = "output_tokens")
    private Integer outputTokens;

    /**
     * AI调用延迟(毫秒)
     */
    @Column(name = "latency_ms")
    private Integer latencyMs;

    /**
     * D1+D5 code validation result (长度+格式校验)
     */
    @Column(name = "validation_pass", nullable = false)
    private Boolean validationPass;

    /**
     * 是否使用了兜底方案
     */
    @Column(name = "fallback_used", nullable = false)
    private Boolean fallbackUsed = false;

    /**
     * Admin最终保存的文本 (for edit_ratio calculation)
     */
    @Column(name = "published_text", columnDefinition = "TEXT")
    private String publishedText;

    /**
     * Levenshtein distance (编辑距离)
     */
    @Column(name = "edit_distance")
    private Integer editDistance;

    /**
     * edit_distance / max(len) (编辑率)
     */
    @Column(name = "edit_ratio")
    private Double editRatio;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 发布时间 (admin保存时填充)
     */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}

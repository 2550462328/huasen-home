package com.huasen.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日访问指标快照实体 (Phase 13)
 *
 * 单表设计 + 指标类型判别字段 (metricType)，由夜间定时任务写入。
 * 每天每种指标类型仅一行，由 (metric_date, metric_type) 唯一约束在 DB 层强制幂等 (D-06)。
 *
 * metricType 取值:
 * - PV_USER   : 门户用户侧页面访问量
 * - PV_MANAGE : 后台管理侧页面访问量
 * - PV_OTHER  : 其他来源访问量
 * - UV        : 独立访客数 (来自当日 UV Set 基数)
 */
@Entity
@Table(
        name = "daily_metric_snapshot",
        schema = "huasen_portal",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_date_type",
                columnNames = {"metric_date", "metric_type"}
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyMetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 指标归属日期 */
    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    /** 指标类型: PV_USER / PV_MANAGE / PV_OTHER / UV */
    @Column(name = "metric_type", nullable = false, length = 32)
    private String metricType;

    /** 指标数值 */
    @Column(name = "metric_value", nullable = false)
    private Long metricValue = 0L;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

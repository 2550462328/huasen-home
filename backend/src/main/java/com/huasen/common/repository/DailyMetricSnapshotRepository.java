package com.huasen.common.repository;

import com.huasen.common.entity.DailyMetricSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日访问指标快照仓储 (Phase 13)
 */
@Repository
public interface DailyMetricSnapshotRepository extends JpaRepository<DailyMetricSnapshot, Long> {

    /**
     * 幂等检查：指定日期+类型的快照是否已存在。
     * 由夜间快照任务在写入前调用，避免重复写入 (RESEARCH Pattern 3 / D-06)。
     */
    boolean existsByMetricDateAndMetricType(LocalDate metricDate, String metricType);

    /**
     * 查询所有快照数据，按日期升序排列 (Phase 13, Plan 04)
     * 用于构建 PV/UV 趋势图的时序数据
     */
    List<DailyMetricSnapshot> findAllByOrderByMetricDateAsc();
}

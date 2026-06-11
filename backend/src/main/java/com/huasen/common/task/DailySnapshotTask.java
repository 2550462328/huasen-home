package com.huasen.common.task;

import com.huasen.common.constant.RedisKeyConstants;
import com.huasen.common.entity.DailyMetricSnapshot;
import com.huasen.common.repository.DailyMetricSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 每日访问指标快照定时任务 (Phase 13)
 *
 * 职责：每晚 00:05 从 Redis 读取昨日的 PV/UV 计数器，持久化到 MySQL，然后删除 Redis 键。
 *
 * 幂等性保证 (RESEARCH Pattern 3 / D-06):
 * - 写入前通过 existsByMetricDateAndMetricType 检查快照是否已存在
 * - 数据库层 (metric_date, metric_type) 唯一约束防止重复行
 * - 双重运行 (手动触发 / 应用重启) 不会损坏数据
 *
 * 事务保证 (RESEARCH anti-patterns):
 * - @Transactional 顺序：先持久化快照，后删除 Redis 键
 * - 持久化失败时回滚，Redis 键保留，可手动恢复
 * - 删除 Redis 键失败时异常传播，不会残留孤儿快照
 *
 * 时区约定 (RESEARCH Pitfall 4):
 * - 使用 Asia/Shanghai 计算"昨天"，避免 UTC 与运维时区不一致
 */
@Component
public class DailySnapshotTask {

    private static final Logger log = LoggerFactory.getLogger(DailySnapshotTask.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DailyMetricSnapshotRepository snapshotRepository;

    /**
     * 夜间快照任务入口
     *
     * 调度时间: 每天 00:05 (Asia/Shanghai)
     * - 00:05 选择理由：午夜过后5分钟，确保日期已翻滚，流量未开始爬坡 (RESEARCH Target 4)
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Shanghai")
    @Transactional
    public void snapshotYesterday() {
        // 1. 计算昨天日期 (Asia/Shanghai 时区)
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        String dayKey = yesterday.format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd

        log.info("Snapshot task started for date: {}", yesterday);

        // 2. 幂等性检查：昨天的快照已存在，跳过
        if (snapshotRepository.existsByMetricDateAndMetricType(yesterday, "PV_USER")) {
            log.info("Snapshot for {} already exists, skipping", yesterday);
            return;
        }

        // 3. 从 Redis 读取昨天的 PV/UV 计数器
        long pvUser = readLong(RedisKeyConstants.PV_KEY_PREFIX + dayKey + ":user");
        long pvManage = readLong(RedisKeyConstants.PV_KEY_PREFIX + dayKey + ":manage");
        long pvOther = readLong(RedisKeyConstants.PV_KEY_PREFIX + dayKey + ":other");
        Long uvSize = redisTemplate.opsForSet().size(RedisKeyConstants.UV_KEY_PREFIX + dayKey);
        long uv = (uvSize != null) ? uvSize : 0L;

        log.info("Read counters for {}: PV_USER={}, PV_MANAGE={}, PV_OTHER={}, UV={}",
                yesterday, pvUser, pvManage, pvOther, uv);

        // 4. 持久化 4 个快照行 (先持久化，后删除 — @Transactional 保证原子性)
        LocalDateTime now = LocalDateTime.now();
        snapshotRepository.save(new DailyMetricSnapshot(null, yesterday, "PV_USER", pvUser, now));
        snapshotRepository.save(new DailyMetricSnapshot(null, yesterday, "PV_MANAGE", pvManage, now));
        snapshotRepository.save(new DailyMetricSnapshot(null, yesterday, "PV_OTHER", pvOther, now));
        snapshotRepository.save(new DailyMetricSnapshot(null, yesterday, "UV", uv, now));

        log.info("Persisted 4 snapshots for {}", yesterday);

        // 5. 删除 Redis 键 (清理昨天的计数器)
        List<String> keysToDelete = List.of(
                RedisKeyConstants.PV_KEY_PREFIX + dayKey + ":user",
                RedisKeyConstants.PV_KEY_PREFIX + dayKey + ":manage",
                RedisKeyConstants.PV_KEY_PREFIX + dayKey + ":other",
                RedisKeyConstants.UV_KEY_PREFIX + dayKey
        );
        redisTemplate.delete(keysToDelete);

        log.info("Snapshot task finished for date: {}", yesterday);
    }

    /**
     * 辅助方法：从 Redis 读取 Long 值，null 返回 0
     *
     * @param key Redis 键
     * @return Long 值，缺失时返回 0
     */
    private long readLong(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return (value != null) ? Long.parseLong(value) : 0L;
    }
}

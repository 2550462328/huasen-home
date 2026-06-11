package com.huasen.common.task;

import com.huasen.common.entity.DailyMetricSnapshot;
import com.huasen.common.repository.DailyMetricSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TDD tests for DailySnapshotTask (Phase 13 Plan 03)
 */
@ExtendWith(MockitoExtension.class)
class DailySnapshotTaskTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private DailyMetricSnapshotRepository snapshotRepository;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private SetOperations<String, String> setOps;

    @InjectMocks
    private DailySnapshotTask task;

    private LocalDate yesterday;
    private String dayKey;

    @BeforeEach
    void setUp() {
        yesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        dayKey = yesterday.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }

    @Test
    void snapshotYesterday_shouldPersistFourSnapshotsAndDeleteRedisKeys_whenSnapshotsDoNotExist() {
        // Arrange
        when(snapshotRepository.existsByMetricDateAndMetricType(yesterday, "PV_USER"))
                .thenReturn(false);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        when(valueOps.get("PV:" + dayKey + ":user")).thenReturn("1250");
        when(valueOps.get("PV:" + dayKey + ":manage")).thenReturn("320");
        when(valueOps.get("PV:" + dayKey + ":other")).thenReturn("85");
        when(setOps.size("UV:" + dayKey)).thenReturn(456L);

        // Act
        task.snapshotYesterday();

        // Assert - verify 4 snapshots saved
        verify(snapshotRepository, times(4)).save(any(DailyMetricSnapshot.class));

        verify(snapshotRepository).save(argThat(snapshot ->
                snapshot.getMetricDate().equals(yesterday) &&
                        snapshot.getMetricType().equals("PV_USER") &&
                        snapshot.getMetricValue() == 1250L
        ));

        verify(snapshotRepository).save(argThat(snapshot ->
                snapshot.getMetricDate().equals(yesterday) &&
                        snapshot.getMetricType().equals("PV_MANAGE") &&
                        snapshot.getMetricValue() == 320L
        ));

        verify(snapshotRepository).save(argThat(snapshot ->
                snapshot.getMetricDate().equals(yesterday) &&
                        snapshot.getMetricType().equals("PV_OTHER") &&
                        snapshot.getMetricValue() == 85L
        ));

        verify(snapshotRepository).save(argThat(snapshot ->
                snapshot.getMetricDate().equals(yesterday) &&
                        snapshot.getMetricType().equals("UV") &&
                        snapshot.getMetricValue() == 456L
        ));

        // Verify Redis keys deleted
        verify(redisTemplate).delete(argThat((List<String> keys) ->
                keys.contains("PV:" + dayKey + ":user") &&
                        keys.contains("PV:" + dayKey + ":manage") &&
                        keys.contains("PV:" + dayKey + ":other") &&
                        keys.contains("UV:" + dayKey)
        ));
    }

    @Test
    void snapshotYesterday_shouldNoOp_whenSnapshotsAlreadyExist() {
        // Arrange - snapshot already exists (idempotency check)
        when(snapshotRepository.existsByMetricDateAndMetricType(yesterday, "PV_USER"))
                .thenReturn(true);

        // Act
        task.snapshotYesterday();

        // Assert - no saves, no deletes
        verify(snapshotRepository, never()).save(any());
        verify(redisTemplate, never()).delete(anyList());
        verify(valueOps, never()).get(anyString());
    }

    @Test
    void snapshotYesterday_shouldHandleNullRedisValues_byDefaultingToZero() {
        // Arrange
        when(snapshotRepository.existsByMetricDateAndMetricType(yesterday, "PV_USER"))
                .thenReturn(false);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        // Redis returns null for missing keys
        when(valueOps.get("PV:" + dayKey + ":user")).thenReturn(null);
        when(valueOps.get("PV:" + dayKey + ":manage")).thenReturn("150");
        when(valueOps.get("PV:" + dayKey + ":other")).thenReturn(null);
        when(setOps.size("UV:" + dayKey)).thenReturn(0L); // empty set

        // Act
        task.snapshotYesterday();

        // Assert - null values become 0
        verify(snapshotRepository).save(argThat(snapshot ->
                snapshot.getMetricType().equals("PV_USER") &&
                        snapshot.getMetricValue() == 0L
        ));

        verify(snapshotRepository).save(argThat(snapshot ->
                snapshot.getMetricType().equals("PV_MANAGE") &&
                        snapshot.getMetricValue() == 150L
        ));

        verify(snapshotRepository).save(argThat(snapshot ->
                snapshot.getMetricType().equals("PV_OTHER") &&
                        snapshot.getMetricValue() == 0L
        ));

        verify(snapshotRepository).save(argThat(snapshot ->
                snapshot.getMetricType().equals("UV") &&
                        snapshot.getMetricValue() == 0L
        ));
    }

    @Test
    void snapshotYesterday_shouldUseAsiaShanghaiTimezone() {
        // Arrange
        when(snapshotRepository.existsByMetricDateAndMetricType(any(), anyString()))
                .thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(valueOps.get(anyString())).thenReturn("100");
        when(setOps.size(anyString())).thenReturn(50L);

        // Act
        task.snapshotYesterday();

        // Assert - verify yesterday is computed with Asia/Shanghai timezone
        LocalDate expectedYesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        verify(snapshotRepository).existsByMetricDateAndMetricType(eq(expectedYesterday), anyString());
    }
}

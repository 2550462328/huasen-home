package com.huasen.common.repository;

import com.huasen.common.entity.AiGenerationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AI生成日志仓储
 */
@Repository
public interface AiGenerationLogRepository extends JpaRepository<AiGenerationLog, Long> {

    /**
     * 按功能类型分页查询，按创建时间倒序
     */
    Page<AiGenerationLog> findByFeatureOrderByCreatedAtDesc(String feature, Pageable pageable);

    /**
     * 按输入hash和prompt版本查找最近的记录（用于缓存查询）
     */
    Optional<AiGenerationLog> findFirstByInputHashAndPromptVersionOrderByCreatedAtDesc(
            String inputHash, String promptVersion);
}

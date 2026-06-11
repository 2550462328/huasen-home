package com.huasen.common.repository;

import com.huasen.common.entity.ColumnEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 栏目数据仓库
 */
public interface ColumnRepository extends JpaRepository<ColumnEntity, Long> {

    List<ColumnEntity> findByCodeLessThanEqual(Integer code);

    List<ColumnEntity> findByCodeLessThanEqualAndEnabledTrue(Integer code);

    List<ColumnEntity> findByEnabledTrue();

    Page<ColumnEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<ColumnEntity> findByCode(Integer code, Pageable pageable);

    Page<ColumnEntity> findByNameContainingIgnoreCaseAndCode(String name, Integer code, Pageable pageable);
}

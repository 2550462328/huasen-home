package com.huasen.common.repository;

import com.huasen.common.entity.Journal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 订阅源数据仓库
 */
public interface JournalRepository extends JpaRepository<Journal, Long> {

    List<Journal> findByCodeLessThanEqual(Integer code);

    List<Journal> findByCodeLessThanEqualAndEnabledTrue(Integer code);

    List<Journal> findByEnabledTrue();

    Page<Journal> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Journal> findByNameContainingIgnoreCaseAndCode(String name, Integer code, Pageable pageable);
}

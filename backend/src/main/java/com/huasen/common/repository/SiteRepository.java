package com.huasen.common.repository;

import com.huasen.common.entity.Site;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 网站链接数据仓库
 */
public interface SiteRepository extends JpaRepository<Site, Long> {

    List<Site> findByCodeLessThanEqual(Integer code);

    List<Site> findByCodeLessThanEqualAndEnabledTrue(Integer code);

    List<Site> findByEnabledTrue();

    Page<Site> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Site> findByCode(Integer code, Pageable pageable);

    Page<Site> findByNameContainingIgnoreCaseAndCode(String name, Integer code, Pageable pageable);
}

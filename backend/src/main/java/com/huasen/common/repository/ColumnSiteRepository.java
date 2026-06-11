package com.huasen.common.repository;

import com.huasen.common.entity.ColumnSite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 栏目-网站关联数据仓库
 */
public interface ColumnSiteRepository extends JpaRepository<ColumnSite, Long> {

    List<ColumnSite> findByColumnId(Long columnId);

    List<ColumnSite> findBySiteId(Long siteId);
}

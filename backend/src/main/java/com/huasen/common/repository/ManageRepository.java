package com.huasen.common.repository;

import com.huasen.common.entity.Manage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 管理员数据仓库
 */
public interface ManageRepository extends JpaRepository<Manage, Long> {

    Optional<Manage> findByAccountId(String accountId);

    boolean existsByAccountId(String accountId);
}

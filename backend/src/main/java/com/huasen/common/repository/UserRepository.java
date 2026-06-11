package com.huasen.common.repository;

import com.huasen.common.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 用户数据仓库
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByAccountId(String accountId);

    boolean existsByAccountId(String accountId);
}

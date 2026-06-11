package com.huasen.common.repository;

import com.huasen.common.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 文章数据仓库
 */
public interface ArticleRepository extends JpaRepository<Article, Long> {

    List<Article> findByCodeLessThanEqual(Integer code);

    Page<Article> findByCodeLessThanEqual(Integer code, Pageable pageable);

    List<Article> findByCodeLessThanEqualAndIsDraftFalse(Integer code);

    Page<Article> findByTitleContainingIgnoreCaseAndManageIdContainingIgnoreCase(
            String title, String manageId, Pageable pageable);

    Page<Article> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    Page<Article> findByManageIdContainingIgnoreCase(String manageId, Pageable pageable);
}

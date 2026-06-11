package com.huasen.admin.service;

import com.huasen.common.entity.Article;
import com.huasen.common.exception.BusinessException;
import com.huasen.common.repository.ArticleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文章管理服务
 * 对应Node.js: article.controller.js中的管理端业务逻辑
 */
@Service
public class ArticleService {

    @Autowired
    private ArticleRepository articleRepository;

    /**
     * 添加文章
     * 对应Node.js: article.controller.add
     */
    @Transactional
    public Article add(Map<String, Object> params) {
        Article article = new Article();
        article.setManageId((String) params.get("manageId"));
        article.setTitle((String) params.get("title"));
        article.setTag((String) params.get("tag"));
        article.setTime((String) params.get("time"));
        article.setContent((String) params.get("content"));
        article.setBannerImg((String) params.get("bannerImg"));

        if (params.get("code") != null) {
            article.setCode(((Number) params.get("code")).intValue());
        }
        if (params.get("isDraft") != null) {
            article.setIsDraft((Boolean) params.get("isDraft"));
        }
        if (params.get("enabled") != null) {
            article.setEnabled((Boolean) params.get("enabled"));
        }

        return articleRepository.save(article);
    }

    /**
     * 删除文章
     * 对应Node.js: article.controller.remove
     */
    @Transactional
    public void remove(Long id) {
        if (!articleRepository.existsById(id)) {
            throw new BusinessException("ERROR", "文章不存在");
        }
        articleRepository.deleteById(id);
    }

    /**
     * 更新文章
     * 对应Node.js: article.controller.update
     */
    @Transactional
    public Article update(Long id, Map<String, Object> params) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ERROR", "文章不存在"));

        if (params.containsKey("manageId")) {
            article.setManageId((String) params.get("manageId"));
        }
        if (params.containsKey("title")) {
            article.setTitle((String) params.get("title"));
        }
        if (params.containsKey("tag")) {
            article.setTag((String) params.get("tag"));
        }
        if (params.containsKey("time")) {
            article.setTime((String) params.get("time"));
        }
        if (params.containsKey("content")) {
            article.setContent((String) params.get("content"));
        }
        if (params.containsKey("bannerImg")) {
            article.setBannerImg((String) params.get("bannerImg"));
        }
        if (params.containsKey("code")) {
            article.setCode(((Number) params.get("code")).intValue());
        }
        if (params.containsKey("isDraft")) {
            article.setIsDraft((Boolean) params.get("isDraft"));
        }
        if (params.containsKey("enabled")) {
            article.setEnabled((Boolean) params.get("enabled"));
        }

        return articleRepository.save(article);
    }

    /**
     * 分页查询文章（管理端）
     * 对应Node.js: article.controller.findAllByPage
     * 支持按title和manageId模糊查询
     */
    public Map<String, Object> findByPage(int pageNo, int pageSize, String title, String manageId) {
        Pageable pageable = PageRequest.of(Math.max(pageNo - 1, 0), pageSize);
        Page<Article> page;

        boolean hasTitle = title != null && !title.isEmpty();
        boolean hasManageId = manageId != null && !manageId.isEmpty();

        if (hasTitle && hasManageId) {
            page = articleRepository.findByTitleContainingIgnoreCaseAndManageIdContainingIgnoreCase(
                    title, manageId, pageable);
        } else if (hasTitle) {
            page = articleRepository.findByTitleContainingIgnoreCase(title, pageable);
        } else if (hasManageId) {
            page = articleRepository.findByManageIdContainingIgnoreCase(manageId, pageable);
        } else {
            page = articleRepository.findAll(pageable);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getContent());
        result.put("total", page.getTotalElements());
        return result;
    }

    /**
     * 查询所有文章列表（管理端）
     * 对应Node.js: article.controller.findAllByList
     */
    public List<Article> findByList() {
        return articleRepository.findAll();
    }

    /**
     * 按权限码查询文章（用户端）
     * 对应Node.js: article.controller.findByCode
     * 筛选出code <= 用户权限码 且 isDraft=false 的文章
     */
    public List<Article> findByCode(Integer userCode) {
        return articleRepository.findByCodeLessThanEqualAndIsDraftFalse(userCode);
    }

    /**
     * 按ID查询文章（用户端）
     * 对应Node.js: article.controller.findById
     * 筛选出code <= 用户权限码的文章
     */
    public Article findById(Long id, Integer userCode) {
        Article article = articleRepository.findById(id).orElse(null);
        if (article == null) {
            return null;
        }
        if (article.getCode() > userCode) {
            return null;
        }
        return article;
    }
}

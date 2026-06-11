package com.huasen.blog.sharon.service;

import com.huasen.blog.sharon.entity.BlogTag;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 博客标签后台管理服务
 * 标签数据未迁移(08-CONTEXT 决策),所有 CRUD 操作返回空/抛错以避免前端崩溃
 */
@Service
public class BlogTagAdminService {

    private static final String NOT_SUPPORTED = "标签功能已下线";

    public List<BlogTag> findAll() {
        return Collections.emptyList();
    }

    public BlogTag save(Map<String, Object> params) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    public BlogTag update(Long tagId, Map<String, Object> params) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    public void remove(Long tagId) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }
}

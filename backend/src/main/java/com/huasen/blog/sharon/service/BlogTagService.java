package com.huasen.blog.sharon.service;

import com.huasen.blog.sharon.entity.BlogTag;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 博客标签前台服务
 * 标签数据未迁移(08-CONTEXT 决策),返回空集合保证前端不报错
 */
@Service
public class BlogTagService {

    /**
     * 查询所有标签
     */
    public List<BlogTag> findAll() {
        return Collections.emptyList();
    }

    /**
     * 按URL查询单个标签详情
     */
    public Optional<BlogTag> findByTagUrl(String tagUrl) {
        return Optional.empty();
    }
}

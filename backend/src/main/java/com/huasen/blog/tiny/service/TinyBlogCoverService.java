package com.huasen.blog.tiny.service;

import com.huasen.common.service.QiniuStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny Blog 预设封面服务
 *
 * 6张预设封面以固定key存放在七牛云,key形如 huasen/tiny-blog-covers/01.jpg,
 * 因此CDN URL稳定可预测。前台编辑器直接使用完整CDN URL作为下拉选项的value,
 * 预览和保存都用同一个完整URL,不再拼接本地相对路径。
 */
@Service
public class TinyBlogCoverService {

    private static final Logger log = LoggerFactory.getLogger(TinyBlogCoverService.class);

    /** 预设封面文件名 */
    private static final List<String> COVER_FILENAMES = List.of(
            "01.jpg", "02.jpg", "03.jpg", "04.jpg", "05.jpg", "06.jpg"
    );

    /** 七牛存储固定前缀(与迁移工具一致: huasen/ + type) */
    private static final String COVER_KEY_PREFIX = "huasen/tiny-blog-covers/";

    @Autowired(required = false)
    private QiniuStorageService qiniuStorageService;

    /**
     * 返回6张预设封面的完整CDN URL列表
     * 未配置七牛时返回空列表(优雅降级)
     */
    public List<String> listCoverUrls() {
        List<String> urls = new ArrayList<>();
        if (qiniuStorageService == null) {
            log.warn("七牛云未配置,无法返回预设封面URL");
            return urls;
        }
        for (String filename : COVER_FILENAMES) {
            urls.add(qiniuStorageService.buildUrl(COVER_KEY_PREFIX + filename));
        }
        return urls;
    }

    /**
     * 根据预设封面文件名构建固定七牛key
     */
    public String buildCoverKey(String filename) {
        return COVER_KEY_PREFIX + filename;
    }

    public List<String> getCoverFilenames() {
        return COVER_FILENAMES;
    }
}

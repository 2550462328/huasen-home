package com.huasen.blog.tiny.controller;

import com.huasen.blog.tiny.service.TinyBlogCoverService;
import com.huasen.common.dto.HuasenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tiny Blog 预设封面控制器
 * 公开访问,无需认证
 * 路径前缀: /api/tiny-blog/covers
 *
 * 返回6张预设封面的完整七牛CDN URL,供后台编辑器封面下拉选择使用。
 */
@RestController
@RequestMapping("/tiny-blog/covers")
public class TinyBlogCoverController {

    @Autowired
    private TinyBlogCoverService tinyBlogCoverService;

    /**
     * 获取预设封面完整CDN URL列表
     * GET /api/tiny-blog/covers
     */
    @GetMapping
    public ResponseEntity<HuasenResponse> list() {
        List<String> urls = tinyBlogCoverService.listCoverUrls();
        return HuasenResponse.success(urls, "查询预设封面成功");
    }
}

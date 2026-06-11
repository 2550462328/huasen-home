package com.huasen.blog.sharon.controller;

import com.huasen.blog.sharon.dto.ArchiveDTO;
import com.huasen.blog.sharon.service.BlogPostService;
import com.huasen.common.dto.HuasenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 博客归档前台控制器
 * 公开访问,无需认证(D-09)
 * 路径前缀: /api/blog-sharon/archives (D-07)
 */
@RestController
@RequestMapping("/blog-sharon/archives")
public class BlogArchiveController {

    @Autowired
    private BlogPostService blogPostService;

    /**
     * 按年归档
     * GET /api/blog-sharon/archives/year
     */
    @GetMapping("/year")
    public ResponseEntity<HuasenResponse> archiveByYear() {
        List<ArchiveDTO> archives = blogPostService.findArchivesByYear();
        return HuasenResponse.success(archives, "查询年度归档成功");
    }

    /**
     * 按年月归档
     * GET /api/blog-sharon/archives/year/month
     */
    @GetMapping("/year/month")
    public ResponseEntity<HuasenResponse> archiveByYearAndMonth() {
        List<ArchiveDTO> archives = blogPostService.findArchivesByYearAndMonth();
        return HuasenResponse.success(archives, "查询月度归档成功");
    }
}

package com.huasen.admin.controller;

import com.huasen.common.dto.HuasenResponse;
import com.huasen.common.util.ParamUtil;
import com.huasen.common.entity.Article;
import com.huasen.common.exception.BusinessException;
import com.huasen.admin.service.ArticleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文章控制器
 * 对应Node.js: article.router.js + article.controller.js
 * 路径: /article/*
 */
@RestController
@RequestMapping("/article")
public class ArticleController {

    @Autowired
    private ArticleService articleService;

    /**
     * 添加文章
     * POST /article/add
     * 对应Node.js: router.post('/add', handleJWT, checkManagePower, handleUselessParams, add)
     */
    @PostMapping("/add")
    public ResponseEntity<HuasenResponse> add(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        Article article = articleService.add(params);
        return HuasenResponse.success(article, "添加文章成功");
    }

    /**
     * 删除文章
     * POST /article/remove
     * 对应Node.js: router.get('/remove', handleJWT, checkManagePower, remove)
     * 注: 统一改为POST
     */
    @PostMapping("/remove")
    public ResponseEntity<HuasenResponse> remove(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        Long id = params.get("id") != null ? ((Number) params.get("id")).longValue() : null;
        if (id == null) {
            throw new BusinessException("ERROR", "缺少参数id");
        }

        articleService.remove(id);
        return HuasenResponse.success(new HashMap<>(), "删除文章成功");
    }

    /**
     * 更新文章
     * POST /article/update
     * 对应Node.js: router.post('/update', handleJWT, checkManagePower, update)
     */
    @PostMapping("/update")
    public ResponseEntity<HuasenResponse> update(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        Long id = params.get("id") != null ? ((Number) params.get("id")).longValue() : null;
        if (id == null) {
            throw new BusinessException("ERROR", "缺少参数id");
        }

        Article article = articleService.update(id, params);
        return HuasenResponse.success(article, "更新文章成功");
    }

    /**
     * 分页查询文章（管理端）
     * POST /article/findByPage
     * 对应Node.js: router.get('/findByPage', handleJWT, checkManagePower, findAllByPage)
     * 注: 统一改为POST
     */
    @PostMapping("/findByPage")
    public ResponseEntity<HuasenResponse> findByPage(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        int pageNo = ParamUtil.getInt(params, "pageNo", 1);
        int pageSize = ParamUtil.getInt(params, "pageSize", 10);
        String title = (String) params.get("title");
        String manageId = (String) params.get("manageId");

        Map<String, Object> result = articleService.findByPage(pageNo, pageSize, title, manageId);
        return HuasenResponse.success(result, "分页查询文章成功");
    }

    /**
     * 查询所有文章列表（管理端）
     * POST /article/findByList
     * 对应Node.js: router.get('/findByList', handleJWT, checkManagePower, findAllByList)
     * 注: 统一改为POST
     */
    @PostMapping("/findByList")
    public ResponseEntity<HuasenResponse> findByList(HttpServletRequest request) {
        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        List<Article> articles = articleService.findByList();
        return HuasenResponse.success(articles, "查询文章成功");
    }

    /**
     * 按ID查询文章（用户端）
     * GET /article/findById?_id=xxx
     * 对应Node.js: router.get('/findById', handleJWT, findById)
     */
    @GetMapping("/findById")
    public ResponseEntity<HuasenResponse> findById(
            @RequestParam("_id") Long id,
            HttpServletRequest request) {

        Integer userCode = (Integer) request.getAttribute("huasenJWT_code");
        if (userCode == null) {
            userCode = 0;
        }

        Article article = articleService.findById(id, userCode);
        if (article == null) {
            return HuasenResponse.success(List.of(), "查询文章成功");
        }
        return HuasenResponse.success(List.of(article), "查询文章成功");
    }

    /**
     * 按权限码查询文章（用户端）
     * POST /article/findByCode
     * 对应Node.js: router.post('/findByCode', handleJWT, findByCode)
     */
    @PostMapping("/findByCode")
    public ResponseEntity<HuasenResponse> findByCode(HttpServletRequest request) {
        Integer userCode = (Integer) request.getAttribute("huasenJWT_code");
        if (userCode == null) {
            userCode = 0;
        }

        List<Article> articles = articleService.findByCode(userCode);
        return HuasenResponse.success(articles, "查询文章成功");
    }
}

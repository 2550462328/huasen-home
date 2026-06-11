package com.huasen.portal.controller;

import com.huasen.common.dto.HuasenResponse;
import com.huasen.common.entity.Site;
import com.huasen.common.util.ParamUtil;
import com.huasen.portal.service.SiteService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 站点控制器
 * 对应Node.js: site.router.js + site.controller.js
 * 路径: /site/*
 */
@RestController
@RequestMapping("/site")
public class SiteController {

    private static final Logger log = LoggerFactory.getLogger(SiteController.class);

    @Autowired
    private SiteService siteService;

    /**
     * 添加站点
     * POST /site/add
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

        Site site = siteService.add(params);
        return HuasenResponse.success(site, "添加站点成功");
    }

    /**
     * 快速添加站点（浏览器插件）
     * POST /site/quick-add
     * 一次性创建站点并绑定到指定栏目，任何登录用户可用（插件收藏）
     */
    @PostMapping("/quick-add")
    public ResponseEntity<HuasenResponse> quickAdd(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        log.info("quick-add: code={}, params={}", code, params);
        if (code == null || code < 0) {
            log.warn("quick-add: 权限拒绝, code={}", code);
            return HuasenResponse.forbidden("需要登录");
        }

        Site site = siteService.quickAdd(params);
        return HuasenResponse.success(site, "快速添加成功");
    }

    /**
     * 收藏预览（浏览器插件）
     * POST /site/preview
     * 爬取目标网址，返回真实 favicon URL + AI 生成的极短描述，供 popup 保存前展示/编辑。
     * 任何登录用户可用（与 quick-add 一致）。非阻塞：失败字段返回 ""，始终 200。
     */
    @PostMapping("/preview")
    public ResponseEntity<HuasenResponse> preview(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 0) {
            return HuasenResponse.forbidden("需要登录");
        }

        String url = (String) params.get("url");
        Map<String, Object> result = siteService.previewSite(url);
        return HuasenResponse.success(result, "预览成功");
    }

    /**
     * 批量添加站点
     * POST /site/addMany
     * 对应Node.js: router.post('/addMany', handleJWT, checkManagePower, handleUselessParams, addMany)
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/addMany")
    public ResponseEntity<HuasenResponse> addMany(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        List<Map<String, Object>> sites = (List<Map<String, Object>>) params.get("sites");
        List<Site> result = siteService.addMany(sites);
        return HuasenResponse.success(result, "导入站点成功");
    }

    /**
     * 删除站点
     * POST /site/remove
     * 对应Node.js: router.post('/remove', handleJWT, checkManagePower, remove)
     */
    @PostMapping("/remove")
    public ResponseEntity<HuasenResponse> remove(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        Object idObj = params.get("_id");
        if (idObj == null) {
            return HuasenResponse.forbidden("缺少参数_id");
        }
        Long id = ((Number) idObj).longValue();
        siteService.remove(id);
        return HuasenResponse.success(new java.util.HashMap<>(), "删除站点成功");
    }

    /**
     * 批量删除站点
     * POST /site/removeMany
     * 对应Node.js: router.post('/removeMany', handleJWT, checkManagePower, removeMany)
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/removeMany")
    public ResponseEntity<HuasenResponse> removeMany(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        List<Number> idNumbers = (List<Number>) params.get("_ids");
        if (idNumbers == null || idNumbers.isEmpty()) {
            return HuasenResponse.forbidden("缺少参数_ids");
        }
        List<Long> ids = idNumbers.stream().map(Number::longValue).toList();
        siteService.removeMany(ids);
        return HuasenResponse.success(new java.util.HashMap<>(), "删除站点成功");
    }

    /**
     * 更新站点
     * POST /site/update
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

        Object idObj = params.get("_id");
        if (idObj == null) {
            return HuasenResponse.forbidden("缺少参数_id");
        }
        Long id = ((Number) idObj).longValue();
        Site site = siteService.update(id, params);
        return HuasenResponse.success(site, "更新站点成功");
    }

    /**
     * 分页查询站点（管理端）
     * POST /site/findByPage
     * 对应Node.js: router.post('/findByPage', handleJWT, checkManagePower, findAllByPage)
     */
    @PostMapping("/findByPage")
    public ResponseEntity<HuasenResponse> findByPage(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer userCode = (Integer) request.getAttribute("huasenJWT_code");
        if (userCode == null || userCode < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        int pageNo = ParamUtil.getInt(params, "pageNo", 1);
        int pageSize = ParamUtil.getInt(params, "pageSize", 10);
        String name = (String) params.get("name");
        Integer code = ParamUtil.getInteger(params, "code", null);

        Map<String, Object> result = siteService.findByPage(pageNo, pageSize, name, code);
        return HuasenResponse.success(result, "分页查询站点成功");
    }

    /**
     * 按权限码查询站点（用户端）
     * POST /site/findByCode
     * 对应Node.js: router.post('/findByCode', handleJWT, findByCode)
     */
    @PostMapping("/findByCode")
    public ResponseEntity<HuasenResponse> findByCode(HttpServletRequest request) {
        Integer userCode = (Integer) request.getAttribute("huasenJWT_code");
        if (userCode == null) {
            userCode = 0;
        }

        List<Site> sites = siteService.findByCode(userCode);
        return HuasenResponse.success(sites, "查询站点成功");
    }

    /**
     * 查询所有站点列表（管理端）
     * POST /site/list
     * 对应Node.js: router.post('/list', handleJWT, checkManagePower, findByList)
     */
    @PostMapping("/list")
    public ResponseEntity<HuasenResponse> list(HttpServletRequest request) {
        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        List<Site> sites = siteService.findByList();
        return HuasenResponse.success(sites, "查询站点成功");
    }

    /**
     * 查询站点标签列表
     * POST /site/siteTagList
     * 对应Node.js: router.post('/siteTagList', handleJWT, checkManagePower, findSiteTagByList)
     */
    @PostMapping("/siteTagList")
    public ResponseEntity<HuasenResponse> siteTagList(HttpServletRequest request) {
        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        List<String> tags = siteService.findSiteTagByList();
        return HuasenResponse.success(tags, "查询站点成功");
    }

    /**
     * 查询站点所属栏目
     * POST /site/siteColumnList
     * 对应Node.js: router.post('/siteColumnList', handleJWT, checkManagePower, findSiteColumnByList)
     */
    @PostMapping("/siteColumnList")
    public ResponseEntity<HuasenResponse> siteColumnList(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        Long siteId = ((Number) params.get("siteId")).longValue();
        List<Long> columnIds = siteService.findSiteColumnByList(siteId);
        return HuasenResponse.success(columnIds, "查询链接所属栏目成功");
    }

    /**
     * 站点绑定栏目
     * POST /site/bindColumn
     * 对应Node.js: router.post('/bindColumn', handleJWT, checkManagePower, bindColumn)
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/bindColumn")
    public ResponseEntity<HuasenResponse> bindColumn(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        Long columnId = ((Number) params.get("columnId")).longValue();
        List<Number> siteNumbers = (List<Number>) params.get("sites");
        List<Long> siteIds = siteNumbers.stream().map(Number::longValue).toList();

        siteService.bindColumn(columnId, siteIds);
        return HuasenResponse.success(new java.util.HashMap<>(), "绑定成功");
    }

    /**
     * 站点解绑栏目
     * POST /site/unbindColumn
     * 对应Node.js: router.post('/unbindColumn', handleJWT, checkManagePower, unbindColumn)
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/unbindColumn")
    public ResponseEntity<HuasenResponse> unbindColumn(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        Long columnId = ((Number) params.get("columnId")).longValue();
        List<Number> siteNumbers = (List<Number>) params.get("sites");
        List<Long> siteIds = siteNumbers.stream().map(Number::longValue).toList();

        siteService.unbindColumn(columnId, siteIds);
        return HuasenResponse.success(new java.util.HashMap<>(), "解绑成功");
    }
}

package com.huasen.admin.controller;

import com.huasen.common.dto.HuasenResponse;
import com.huasen.common.util.ParamUtil;
import com.huasen.admin.service.BlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 黑名单控制器
 * 对应Node.js: blacklist.router.js + blacklist.controller.js
 * 路径: /blacklist/*
 */
@RestController
@RequestMapping("/blacklist")
public class BlacklistController {

    @Autowired
    private BlacklistService blacklistService;

    /**
     * 添加黑名单IP
     * POST /blacklist/add
     * 对应Node.js: router.post('/add', handleJWT, checkManagePower, add)
     */
    @PostMapping("/add")
    public ResponseEntity<HuasenResponse> add(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        String ip = (String) params.get("ip");
        blacklistService.add(ip);
        return HuasenResponse.success(new HashMap<>(), "黑名单添加成功");
    }

    /**
     * 删除黑名单IP
     * POST /blacklist/remove
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

        String ip = (String) params.get("ip");
        blacklistService.remove(ip);
        return HuasenResponse.success(new HashMap<>(), "黑名单移除成功");
    }

    /**
     * 分页查询黑名单（管理端）
     * POST /blacklist/findByPage
     * 对应Node.js: router.post('/findByPage', handleJWT, checkManagePower, findAllByPage)
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
        String ip = (String) params.get("ip");

        Map<String, Object> result = blacklistService.findByPage(pageNo, pageSize, ip);
        return HuasenResponse.success(result, "黑名单查询成功");
    }
}

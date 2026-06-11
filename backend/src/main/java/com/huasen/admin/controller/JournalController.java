package com.huasen.admin.controller;

import com.huasen.common.dto.HuasenResponse;
import com.huasen.common.entity.Journal;
import com.huasen.common.exception.BusinessException;
import com.huasen.common.util.ParamUtil;
import com.huasen.admin.service.JournalService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订阅源控制器
 * 对应Node.js: journal.router.js + journal.controller.js
 * 路径: /journal/*
 */
@RestController
@RequestMapping("/journal")
public class JournalController {

    @Autowired
    private JournalService journalService;

    /**
     * 添加订阅源
     * POST /journal/add
     * 对应Node.js: router.post('/add', handleUselessParams, add)
     */
    @PostMapping("/add")
    public ResponseEntity<HuasenResponse> add(@RequestBody Map<String, Object> params) {
        Journal journal = journalService.add(params);
        return HuasenResponse.success(journal, "添加订阅源成功");
    }

    /**
     * 删除订阅源
     * POST /journal/remove
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

        Long id = ParamUtil.getLong(params, "_id", null);
        if (id == null) {
            throw new BusinessException("ERROR", "缺少参数_id");
        }

        journalService.remove(id);
        return HuasenResponse.success(new HashMap<>(), "删除订阅源成功");
    }

    /**
     * 更新订阅源
     * POST /journal/update
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

        Long id = ParamUtil.getLong(params, "_id", null);
        if (id == null) {
            throw new BusinessException("ERROR", "缺少参数_id");
        }

        Journal journal = journalService.update(id, params);
        return HuasenResponse.success(journal, "更新订阅源成功");
    }

    /**
     * 分页查询订阅源（管理端）
     * POST /journal/findByPage
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
        String name = (String) params.get("name");
        Integer filterCode = ParamUtil.getInteger(params, "code", null);

        Map<String, Object> result = journalService.findByPage(pageNo, pageSize, name, filterCode);
        return HuasenResponse.success(result, "分页查询成功");
    }

    /**
     * 按权限码查询订阅源（用户端）
     * POST /journal/findByCode
     * 对应Node.js: router.post('/findByCode', handleJWT, findByCode)
     */
    @PostMapping("/findByCode")
    public ResponseEntity<HuasenResponse> findByCode(HttpServletRequest request) {
        Integer userCode = (Integer) request.getAttribute("huasenJWT_code");
        if (userCode == null) {
            userCode = 0;
        }

        List<Journal> journals = journalService.findByCode(userCode);
        return HuasenResponse.success(journals, "查询订阅源成功");
    }

    /**
     * 查询所有订阅源（用户端）
     * POST /journal/findAll
     * 对应Node.js: router.post('/findAll', findAll)
     */
    @PostMapping("/findAll")
    public ResponseEntity<HuasenResponse> findAll() {
        List<Map<String, Object>> journals = journalService.findAll();
        return HuasenResponse.success(journals, "查询站点成功");
    }

    /**
     * 按ID查询订阅源详细信息（包含栏目和站点）
     * POST /journal/findJournalInformationById
     * 对应Node.js: router.post('/findJournalInformationById', handleJWT, findJournalInformationById)
     */
    @PostMapping("/findJournalInformationById")
    public ResponseEntity<HuasenResponse> findJournalInformationById(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer userCode = (Integer) request.getAttribute("huasenJWT_code");
        if (userCode == null) {
            userCode = 0;
        }

        Long id = null;
        if (params.get("id") != null) {
            id = ((Number) params.get("id")).longValue();
        } else if (params.get("_id") != null) {
            id = Long.valueOf(params.get("_id").toString());
        }

        if (id == null) {
            throw new BusinessException("ERROR", "缺少参数id");
        }

        Map<String, Object> result = journalService.findJournalInformationById(id, userCode);
        if (result == null) {
            return HuasenResponse.error("订阅源已废弃");
        }
        return HuasenResponse.success(result, "查询订阅成功");
    }
}

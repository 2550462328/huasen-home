package com.huasen.portal.controller;

import com.huasen.common.dto.HuasenResponse;
import com.huasen.common.entity.ColumnEntity;
import com.huasen.common.exception.BusinessException;
import com.huasen.common.util.ParamUtil;
import com.huasen.portal.service.ColumnService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 栏目控制器
 * 对应Node.js: column.router.js + column.controller.js
 * 路径: /column/*
 */
@RestController
@RequestMapping("/column")
public class ColumnController {

    @Autowired
    private ColumnService columnService;

    /**
     * 添加栏目
     * POST /column/add
     * 对应Node.js: router.post('/add', handleUselessParams, add)
     * 注意: Node.js中此接口无JWT和权限校验，但为安全起见仍做管理员校验
     */
    @PostMapping("/add")
    public ResponseEntity<HuasenResponse> add(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        ColumnEntity column = columnService.add(params);
        return HuasenResponse.success(column, "添加栏目成功");
    }

    /**
     * 删除栏目
     * POST /column/remove
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
        columnService.remove(id);
        return HuasenResponse.success(new java.util.HashMap<>(), "删除栏目成功");
    }

    /**
     * 更新栏目
     * POST /column/update
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
        ColumnEntity column = columnService.update(id, params);
        return HuasenResponse.success(column, "更新栏目成功");
    }

    /**
     * 分页查询栏目（管理端）
     * POST /column/findByPage
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

        Map<String, Object> result = columnService.findByPage(pageNo, pageSize, name, code);
        return HuasenResponse.success(result, "分页查询成功");
    }

    /**
     * 按权限码查询栏目（用户端）
     * POST /column/findByCode
     * 对应Node.js: router.post('/findByCode', handleJWT, findByCode)
     */
    @PostMapping("/findByCode")
    public ResponseEntity<HuasenResponse> findByCode(HttpServletRequest request) {
        Integer userCode = (Integer) request.getAttribute("huasenJWT_code");
        if (userCode == null) {
            userCode = 0;
        }

        List<ColumnEntity> columns = columnService.findByCode(userCode);
        return HuasenResponse.success(columns, "查询栏目成功");
    }

    /**
     * 查询所有栏目列表（管理端）
     * POST /column/list
     * 对应Node.js: router.post('/list', handleJWT, checkManagePower, findByList)
     */
    @PostMapping("/list")
    public ResponseEntity<HuasenResponse> list(HttpServletRequest request) {
        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        List<ColumnEntity> columns = columnService.findByList();
        return HuasenResponse.success(columns, "查询栏目成功");
    }

    /**
     * 绑定站点到栏目
     * POST /column/bindSite
     * 对应Node.js: router.post('/bindSite', handleJWT, checkManagePower, bindSite)
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/bindSite")
    public ResponseEntity<HuasenResponse> bindSite(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        List<Number> columnNumbers = (List<Number>) params.get("columnIds");
        List<Long> columnIds = columnNumbers.stream().map(Number::longValue).toList();
        List<Number> siteNumbers = (List<Number>) params.get("siteIds");
        List<Long> siteIds = siteNumbers.stream().map(Number::longValue).toList();

        columnService.bindSite(columnIds, siteIds);
        return HuasenResponse.success(new java.util.HashMap<>(), "绑定成功");
    }

    /**
     * 从栏目解绑站点
     * POST /column/unbindSite
     * 对应Node.js: router.post('/unbindSite', handleJWT, checkManagePower, unbindSite)
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/unbindSite")
    public ResponseEntity<HuasenResponse> unbindSite(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        List<Number> columnNumbers = (List<Number>) params.get("columnIds");
        List<Long> columnIds = columnNumbers.stream().map(Number::longValue).toList();
        List<Number> siteNumbers = (List<Number>) params.get("siteIds");
        List<Long> siteIds = siteNumbers.stream().map(Number::longValue).toList();

        columnService.unbindSite(columnIds, siteIds);
        return HuasenResponse.success(new java.util.HashMap<>(), "解绑成功");
    }
}

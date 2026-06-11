package com.huasen.admin.controller;

import com.huasen.admin.service.DashboardService;
import com.huasen.common.dto.HuasenResponse;
import com.huasen.common.util.ParamUtil;
import com.huasen.common.exception.BusinessException;
import com.huasen.common.service.AppConfigService;
import com.huasen.common.service.FileUploadService;
import com.huasen.common.service.RuntimeCodeExecutor;
import com.huasen.admin.service.ManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/manage")
public class ManageController {

    @Autowired
    private ManageService manageService;

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private RuntimeCodeExecutor runtimeCodeExecutor;

    @Autowired
    private AppConfigService appConfigService;

    @Autowired
    private DashboardService dashboardService;

    @PostMapping("/login")
    public ResponseEntity<HuasenResponse> login(@RequestBody Map<String, Object> params) {
        String id = (String) params.get("id");
        String password = (String) params.get("password");

        Map<String, Object> data = manageService.login(id, password);
        return HuasenResponse.success(data, "登录成功");
    }

    @PostMapping("/add")
    public ResponseEntity<HuasenResponse> add(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("huasenJWT_code") Integer jwtCode) {

        if (jwtCode < 3) {
            throw new BusinessException("FORBIDDEN", "权限不足");
        }

        String id = (String) params.get("id");
        String password = (String) params.get("password");
        String name = (String) params.get("name");
        Integer code = ParamUtil.getInteger(params, "code", null);

        manageService.add(id, password, name, code);
        return HuasenResponse.success(new HashMap<>(), "添加管理员成功");
    }

    @PostMapping("/findByPage")
    public ResponseEntity<HuasenResponse> findByPage(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("huasenJWT_code") Integer jwtCode) {

        if (jwtCode < 2) {
            throw new BusinessException("FORBIDDEN", "权限不足");
        }

        int pageNo = ParamUtil.getInt(params, "pageNo", 1);
        int pageSize = ParamUtil.getInt(params, "pageSize", 10);

        Map<String, Object> data = manageService.findByPage(pageNo, pageSize);
        return HuasenResponse.success(data, "分页查询管理员成功");
    }

    @PostMapping("/remove")
    public ResponseEntity<HuasenResponse> remove(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("huasenJWT_code") Integer jwtCode) {

        if (jwtCode < 3) {
            throw new BusinessException("FORBIDDEN", "权限不足");
        }

        Long id = params.get("_id") != null ? ((Number) params.get("_id")).longValue() : null;
        if (id == null) {
            throw new BusinessException("ERROR", "缺少参数_id");
        }

        manageService.remove(id);
        return HuasenResponse.success(new HashMap<>(), "删除管理员成功");
    }

    @PostMapping("/update")
    public ResponseEntity<HuasenResponse> update(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("huasenJWT_key") String jwtKey,
            @RequestAttribute("huasenJWT_code") Integer jwtCode) {

        if (jwtCode < 2) {
            throw new BusinessException("FORBIDDEN", "权限不足");
        }

        Long id = params.get("_id") != null ? ((Number) params.get("_id")).longValue() : null;
        if (id == null) {
            throw new BusinessException("ERROR", "缺少参数_id");
        }

        String accountId = (String) params.get("id");
        String password = (String) params.get("password");
        String name = (String) params.get("name");
        Integer code = ParamUtil.getInteger(params, "code", null);

        manageService.update(id, accountId, password, name, code, jwtCode, jwtKey);
        return HuasenResponse.success(new HashMap<>(), "更新管理员成功");
    }

    @PostMapping("/upload")
    public ResponseEntity<HuasenResponse> upload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "type", defaultValue = "default") String type,
            @RequestAttribute("huasenJWT_code") Integer jwtCode) throws IOException {

        if (jwtCode < 1) {
            return HuasenResponse.forbidden("权限不足");
        }

        List<Map<String, String>> result = fileUploadService.upload(files, type);
        return HuasenResponse.success(result, "上传成功");
    }

    @PostMapping("/uploadIcon")
    public ResponseEntity<HuasenResponse> uploadIcon(
            @RequestParam("files") MultipartFile[] files,
            @RequestAttribute("huasenJWT_code") Integer jwtCode) throws IOException {

        if (jwtCode < 1) {
            return HuasenResponse.forbidden("权限不足");
        }

        List<Map<String, String>> result = fileUploadService.upload(files, "icon");
        return HuasenResponse.success(result, "上传成功");
    }

    @PostMapping("/overview")
    public ResponseEntity<HuasenResponse> overview(
            @RequestAttribute("huasenJWT_code") Integer jwtCode) {

        if (jwtCode < 1) {
            return HuasenResponse.forbidden("权限不足");
        }

        Map<String, Object> data = manageService.getOverview();
        return HuasenResponse.success(data, "查询概览成功");
    }

    @PostMapping("/diskOverview")
    public ResponseEntity<HuasenResponse> diskOverview(
            @RequestAttribute("huasenJWT_code") Integer jwtCode) {

        if (jwtCode < 1) {
            return HuasenResponse.forbidden("权限不足");
        }

        Map<String, Object> data = manageService.getDiskOverview();
        return HuasenResponse.success(data, "查询磁盘报表成功");
    }

    @PostMapping("/visitor")
    public ResponseEntity<HuasenResponse> visitor(
            @RequestAttribute("huasenJWT_code") Integer jwtCode) {

        if (jwtCode < 1) {
            return HuasenResponse.forbidden("权限不足");
        }

        Map<String, Object> data = manageService.getVisitor();
        return HuasenResponse.success(data, "查询访客统计成功");
    }

    @PostMapping("/executeRuntimeCode")
    public ResponseEntity<HuasenResponse> executeRuntimeCode(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("huasenJWT_code") Integer jwtCode) {

        if (jwtCode != 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        String runtimeScript = (String) params.get("runtimeScript");
        if (runtimeScript == null || runtimeScript.isBlank()) {
            return HuasenResponse.error("缺少执行脚本");
        }

        String result = runtimeCodeExecutor.execute(runtimeScript);
        if (result.startsWith("Error:") || result.startsWith("ScriptError:")) {
            return HuasenResponse.error(result);
        }
        return HuasenResponse.success(result, "执行成功");
    }

    @PostMapping("/findAppConfig")
    public ResponseEntity<HuasenResponse> findAppConfig(
            @RequestAttribute("huasenJWT_code") Integer jwtCode) {

        if (jwtCode < 1) {
            return HuasenResponse.forbidden("权限不足");
        }

        Map<String, Object> config = appConfigService.loadConfig();
        return HuasenResponse.success(config, "查询配置成功");
    }

    @PostMapping("/saveAppConfig")
    @SuppressWarnings("unchecked")
    public ResponseEntity<HuasenResponse> saveAppConfig(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("huasenJWT_code") Integer jwtCode) {

        if (jwtCode != 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        Map<String, Object> systemConfig = (Map<String, Object>) params.get("systemConfig");
        if (systemConfig == null) {
            systemConfig = params;
        }

        boolean saved = appConfigService.saveConfig(systemConfig);
        if (saved) {
            return HuasenResponse.success(new HashMap<>(), "保存配置成功");
        }
        return HuasenResponse.error("保存配置失败");
    }

    @PostMapping("/findAppFavicon")
    public ResponseEntity<HuasenResponse> findAppFavicon(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("huasenJWT_code") Integer jwtCode) {

        if (jwtCode < 1) {
            return HuasenResponse.forbidden("权限不足");
        }

        String url = (String) params.get("url");
        if (url == null || url.isBlank()) {
            return HuasenResponse.success(Map.of("icons", List.of(), "description", ""), "查询图标失败");
        }

        Map<String, Object> result = manageService.fetchFavicons(url);
        return HuasenResponse.success(result, "查询图标成功");
    }

    @PostMapping("/dashboard/overview")
    public ResponseEntity<HuasenResponse> dashboardOverview(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("huasenJWT_code") Integer jwtCode) {

        if (jwtCode < 1) {
            throw new BusinessException("FORBIDDEN", "权限不足");
        }

        Map<String, Object> data = dashboardService.getOverview();
        return HuasenResponse.success(data, "查询成功");
    }

    @PostMapping("/dashboard/pvTrend")
    public ResponseEntity<HuasenResponse> dashboardPvTrend(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("huasenJWT_code") Integer jwtCode) {

        if (jwtCode < 1) {
            throw new BusinessException("FORBIDDEN", "权限不足");
        }

        Object granularity = params == null ? null : params.get("granularity");
        String scope = granularity == null ? "day" : granularity.toString();
        Map<String, Object> data = dashboardService.getPvTrend(scope);
        return HuasenResponse.success(data, "查询成功");
    }

    @PostMapping("/dashboard/articleRank")
    public ResponseEntity<HuasenResponse> dashboardArticleRank(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("huasenJWT_code") Integer jwtCode) {

        if (jwtCode < 1) {
            throw new BusinessException("FORBIDDEN", "权限不足");
        }

        List<Map<String, Object>> data = dashboardService.getArticleRank(10);
        return HuasenResponse.success(data, "查询成功");
    }
}

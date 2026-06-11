package com.huasen.portal.controller;

import com.huasen.common.dto.HuasenResponse;
import com.huasen.common.entity.User;
import com.huasen.common.service.AppConfigService;
import com.huasen.portal.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private AppConfigService appConfigService;

    @PostMapping("/login")
    public ResponseEntity<HuasenResponse> login(@RequestBody Map<String, Object> params) {
        String id = (String) params.get("id");
        String password = (String) params.get("password");

        Map<String, Object> data = userService.login(id, password);
        return HuasenResponse.success(data, "登录成功");
    }

    @PostMapping("/register")
    public ResponseEntity<HuasenResponse> register(@RequestBody Map<String, Object> params) {
        String id = (String) params.get("id");
        String password = (String) params.get("password");

        userService.register(id, password);
        return HuasenResponse.success(new java.util.HashMap<>(), "注册用户成功");
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/backup")
    public ResponseEntity<HuasenResponse> backup(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("huasenJWT_key") String jwtKey) {

        List<Map<String, Object>> records = (List<Map<String, Object>>) params.get("records");
        Map<String, Object> config = (Map<String, Object>) params.get("config");
        String name = (String) params.get("name");
        String headImg = (String) params.get("headImg");

        userService.backup(jwtKey, records, config, name, headImg);
        return HuasenResponse.success(new java.util.HashMap<>(), "更新成功");
    }

    @PostMapping("/consistentFromCloud")
    public ResponseEntity<HuasenResponse> consistentFromCloud(
            @RequestAttribute("huasenJWT_key") String jwtKey) {

        Map<String, Object> data = userService.consistentFromCloud(jwtKey);
        return HuasenResponse.success(data, "同步云端数据成功");
    }

    @PostMapping("/findAppConfig")
    public ResponseEntity<HuasenResponse> findAppConfig() {
        // 从数据库读取配置（已过滤敏感信息）
        Map<String, Object> config = appConfigService.loadConfigForUser();
        return HuasenResponse.success(config, "查询配置成功");
    }

    @PostMapping("/updatePassword")
    public ResponseEntity<HuasenResponse> updatePassword(@RequestBody Map<String, Object> params) {
        String id = (String) params.get("id");
        String password = (String) params.get("password");

        userService.updatePassword(id, password);
        return HuasenResponse.success(new java.util.HashMap<>(), "更新用户密码成功");
    }

    @PostMapping("/add")
    public ResponseEntity<HuasenResponse> add(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("huasenJWT_code") Integer code) {

        if (code < 1) {
            return HuasenResponse.forbidden("权限不足");
        }

        String id = (String) params.get("id");
        String password = (String) params.get("password");
        String name = (String) params.get("name");
        Integer userCode = toInteger(params.get("code"), 0);
        Boolean enabled = params.get("enabled") != null ? (Boolean) params.get("enabled") : true;

        User user = userService.addUser(id, password, name, userCode, enabled);
        return HuasenResponse.success(user, "添加用户成功");
    }

    @PostMapping("/findByPage")
    public ResponseEntity<HuasenResponse> findByPage(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("huasenJWT_code") Integer code) {

        if (code < 1) {
            return HuasenResponse.forbidden("权限不足");
        }

        int pageNo = toInteger(params.get("pageNo"), 1);
        int pageSize = toInteger(params.get("pageSize"), 10);
        String id = (String) params.get("id");
        String name = (String) params.get("name");
        Integer filterCode = toInteger(params.get("code"), null);

        Map<String, Object> data = userService.findByPage(pageNo, pageSize, id, name, filterCode);
        return HuasenResponse.success(data, "查询用户成功");
    }

    @PostMapping("/remove")
    public ResponseEntity<HuasenResponse> remove(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("huasenJWT_code") Integer code) {

        if (code != 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        Long id = ((Number) params.get("_id")).longValue();
        userService.removeUser(id);
        return HuasenResponse.success(new java.util.HashMap<>(), "删除用户成功");
    }

    @PostMapping("/update")
    public ResponseEntity<HuasenResponse> update(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("huasenJWT_code") Integer code) {

        if (code != 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        Long id = ((Number) params.get("_id")).longValue();
        userService.updateUser(id, params);
        return HuasenResponse.success(new java.util.HashMap<>(), "更新用户成功");
    }

    /**
     * 安全地将请求参数转换为 Integer。
     * 前端可能传 Number、数字字符串或空字符串，统一处理避免 ClassCastException。
     *
     * @param value        参数原始值
     * @param defaultValue 当值为 null 或空白时返回的默认值
     */
    private Integer toInteger(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(text);
    }
}

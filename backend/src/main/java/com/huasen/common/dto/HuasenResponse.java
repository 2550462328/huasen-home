package com.huasen.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

/**
 * 统一响应格式
 * 对应Node.js: formatResponseData(data, tag, msg, isSecret)
 * 响应结构: {code, msg, data}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HuasenResponse {

    /** HTTP状态码: 200/400/401/403 */
    private Integer code;

    /** 响应消息: "请求成功·xxx" / "请求失败·xxx" */
    private String msg;

    /** 响应数据: 成功时为实际数据，失败时为空Map {} */
    private Object data;

    /**
     * 成功响应
     * 对应Node.js: responseData(res, data, 'SUCCESS', msg, false)
     */
    public static ResponseEntity<HuasenResponse> success(Object data, String msg) {
        HuasenResponse response = new HuasenResponse(200, "请求成功·" + msg, data);
        return ResponseEntity.status(200).body(response);
    }

    /**
     * 错误响应
     * 对应Node.js: responseData(res, {}, 'ERROR', msg, false)
     */
    public static ResponseEntity<HuasenResponse> error(String msg) {
        HuasenResponse response = new HuasenResponse(400, "请求失败·" + msg, new HashMap<>());
        return ResponseEntity.status(400).body(response);
    }

    /**
     * 权限禁止响应
     * 对应Node.js: responseData(res, {}, 'FORBIDDEN', msg, false)
     */
    public static ResponseEntity<HuasenResponse> forbidden(String msg) {
        HuasenResponse response = new HuasenResponse(403, "请求禁止·" + msg, new HashMap<>());
        return ResponseEntity.status(403).body(response);
    }

    /**
     * 认证无效响应
     * 对应Node.js: responseData(res, {}, 'AUTH', msg, false)
     */
    public static ResponseEntity<HuasenResponse> auth(String msg) {
        HuasenResponse response = new HuasenResponse(401, "请求无效·" + msg, new HashMap<>());
        return ResponseEntity.status(401).body(response);
    }
}

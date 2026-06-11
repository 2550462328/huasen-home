package com.huasen.common.util;

import java.util.Map;

/**
 * 请求参数解析工具
 * 前端（ElementUI 表单/下拉框、空字符串默认值）可能把数字字段以字符串形式发送，
 * 例如 {"code": "", "pageNo": "1"}。直接强转 (Number) 会抛 ClassCastException。
 * 本工具做容错解析：支持 Number、数字字符串，空串/null/非法值返回默认值。
 */
public final class ParamUtil {

    private ParamUtil() {
    }

    /**
     * 从参数 Map 中解析整数，容错处理 String / Number / 空值。
     *
     * @param params       参数 Map
     * @param key          字段名
     * @param defaultValue 缺失、空串或无法解析时的默认值
     * @return 解析后的 Integer（可能为 defaultValue）
     */
    public static Integer getInteger(Map<String, Object> params, String key, Integer defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            String str = ((String) value).trim();
            if (str.isEmpty()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 解析整数基本类型，缺失时返回默认值（用于 pageNo/pageSize 等必有默认值的场景）。
     */
    public static int getInt(Map<String, Object> params, String key, int defaultValue) {
        Integer result = getInteger(params, key, defaultValue);
        return result != null ? result : defaultValue;
    }

    /**
     * 从参数 Map 中解析 Long，容错处理 String / Number / 空值。
     * 前端沿用 MongoDB 时期的 _id 约定，主键以数字或数字字符串形式发送。
     *
     * @param params       参数 Map
     * @param key          字段名
     * @param defaultValue 缺失、空串或无法解析时的默认值
     * @return 解析后的 Long（可能为 defaultValue）
     */
    public static Long getLong(Map<String, Object> params, String key, Long defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            String str = ((String) value).trim();
            if (str.isEmpty()) {
                return defaultValue;
            }
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}

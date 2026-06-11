package com.huasen.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 黑名单管理服务
 * 对应Node.js: blacklist.controller.js中的业务逻辑
 * 使用Redis Hash存储黑名单IP
 */
@Service
public class BlacklistService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String POOL_BLACKLIST = "POOL_BLACKLIST";

    /**
     * 添加黑名单IP
     * 对应Node.js: blacklist.controller.add
     * IP作为键值对存入Redis Hash
     */
    public void add(String ip) {
        if (ip == null || ip.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForHash().put(POOL_BLACKLIST, ip, ip);
    }

    /**
     * 删除黑名单IP
     * 对应Node.js: blacklist.controller.remove
     */
    public void remove(String ip) {
        if (ip == null || ip.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForHash().delete(POOL_BLACKLIST, ip);
    }

    /**
     * 分页查询黑名单（管理端）
     * 对应Node.js: blacklist.controller.findAllByPage
     * 从Redis Hash中读取所有IP，进行模糊过滤和手动分页
     */
    public Map<String, Object> findByPage(int pageNo, int pageSize, String ip) {
        Map<Object, Object> pool = stringRedisTemplate.opsForHash().entries(POOL_BLACKLIST);

        // 转换为列表
        List<Map<String, Object>> temp = pool.values().stream()
                .map(value -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("ip", value.toString());
                    return item;
                })
                .collect(Collectors.toList());

        // 模糊过滤
        List<Map<String, Object>> filterList = temp;
        if (ip != null && !ip.isEmpty()) {
            filterList = temp.stream()
                    .filter(item -> item.get("ip").toString().contains(ip))
                    .collect(Collectors.toList());
        }

        // 手动分页
        int startIndex = (pageNo - 1) * pageSize;
        int endIndex = Math.min(pageNo * pageSize, filterList.size());
        List<Map<String, Object>> list = filterList.subList(
                Math.max(0, startIndex),
                Math.max(0, endIndex)
        );

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", filterList.size());
        return result;
    }
}

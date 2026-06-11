package com.huasen.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huasen.common.entity.ColumnEntity;
import com.huasen.common.entity.ColumnSite;
import com.huasen.common.entity.Journal;
import com.huasen.common.entity.Site;
import com.huasen.common.exception.BusinessException;
import com.huasen.common.repository.ColumnRepository;
import com.huasen.common.repository.ColumnSiteRepository;
import com.huasen.common.repository.JournalRepository;
import com.huasen.common.repository.SiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 订阅源管理服务
 * 对应Node.js: journal.controller.js中的业务逻辑
 */
@Service
public class JournalService {

    @Autowired
    private JournalRepository journalRepository;

    @Autowired
    private ColumnRepository columnRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private ColumnSiteRepository columnSiteRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 添加订阅源
     * 对应Node.js: journal.controller.add
     */
    @Transactional
    public Journal add(Map<String, Object> params) {
        Journal journal = new Journal();
        journal.setName((String) params.get("name"));
        journal.setColumnStore((String) params.get("columnStore"));
        journal.setExpand((String) params.get("expand"));

        if (params.get("enabled") != null) {
            journal.setEnabled((Boolean) params.get("enabled"));
        }
        if (params.get("code") != null) {
            journal.setCode(((Number) params.get("code")).intValue());
        }

        return journalRepository.save(journal);
    }

    /**
     * 删除订阅源
     * 对应Node.js: journal.controller.remove
     */
    @Transactional
    public void remove(Long id) {
        if (!journalRepository.existsById(id)) {
            throw new BusinessException("ERROR", "订阅源不存在");
        }
        journalRepository.deleteById(id);
    }

    /**
     * 更新订阅源
     * 对应Node.js: journal.controller.update
     */
    @Transactional
    public Journal update(Long id, Map<String, Object> params) {
        Journal journal = journalRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ERROR", "订阅源不存在"));

        if (params.containsKey("name")) {
            journal.setName((String) params.get("name"));
        }
        if (params.containsKey("columnStore")) {
            journal.setColumnStore((String) params.get("columnStore"));
        }
        if (params.containsKey("expand")) {
            journal.setExpand((String) params.get("expand"));
        }
        if (params.containsKey("enabled")) {
            journal.setEnabled((Boolean) params.get("enabled"));
        }
        if (params.containsKey("code")) {
            journal.setCode(((Number) params.get("code")).intValue());
        }

        return journalRepository.save(journal);
    }

    /**
     * 分页查询订阅源（管理端）
     * 对应Node.js: journal.controller.findAllByPage
     */
    public Map<String, Object> findByPage(int pageNo, int pageSize, String name, Integer code) {
        Pageable pageable = PageRequest.of(Math.max(pageNo - 1, 0), pageSize);
        Page<Journal> page;

        boolean hasName = name != null && !name.isEmpty();
        boolean hasCode = code != null;

        if (hasName && hasCode) {
            page = journalRepository.findByNameContainingIgnoreCaseAndCode(name, code, pageable);
        } else if (hasName) {
            page = journalRepository.findByNameContainingIgnoreCase(name, pageable);
        } else {
            page = journalRepository.findAll(pageable);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getContent());
        result.put("total", page.getTotalElements());
        return result;
    }

    /**
     * 按权限码查询订阅源（用户端）
     * 对应Node.js: journal.controller.findByCode
     * 筛选出code <= 用户权限码 且 enabled=true 的订阅源
     */
    public List<Journal> findByCode(Integer userCode) {
        return journalRepository.findByCodeLessThanEqualAndEnabledTrue(userCode);
    }

    /**
     * 查询所有订阅源（用户端）
     * 对应Node.js: journal.controller.findAll
     * 只返回enabled=true的订阅源，且只返回部分字段
     */
    public List<Map<String, Object>> findAll() {
        List<Journal> journals = journalRepository.findByEnabledTrue();
        return journals.stream().map(journal -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", journal.getId());
            map.put("_id", journal.getId());
            map.put("name", journal.getName());
            map.put("expand", journal.getExpand());
            map.put("code", journal.getCode());
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 按ID查询订阅源详细信息（包含栏目和站点）
     * 对应Node.js: journal.controller.findJournalInformationById
     * 返回订阅源下的栏目和站点信息
     */
    public Map<String, Object> findJournalInformationById(Long id, Integer userCode) {
        Optional<Journal> journalOpt = journalRepository.findById(id);
        if (journalOpt.isEmpty()) {
            return null;
        }

        Journal journal = journalOpt.get();
        // columnStore 可能存历史 mongo_id 字符串（迁移数据）或新建栏目的数值主键 id（ColumnSelector 当前写入），统一按字符串解析后双 key 匹配
        List<String> columnStoreKeys = parseColumnStoreAsStrings(journal.getColumnStore());

        // 查询所有符合权限的栏目和站点
        List<ColumnEntity> allColumns = columnRepository.findByCodeLessThanEqualAndEnabledTrue(userCode);
        List<Site> allSites = siteRepository.findByCodeLessThanEqualAndEnabledTrue(userCode);

        // 同时按数值主键 id 与 mongo_id 建索引，兼容历史 mongo_id 与新建数值 id 两种 columnStore 格式
        Map<String, ColumnEntity> columnByKey = new HashMap<>();
        for (ColumnEntity col : allColumns) {
            if (col.getId() != null) {
                columnByKey.putIfAbsent(String.valueOf(col.getId()), col);
            }
            if (col.getMongoId() != null && !col.getMongoId().isEmpty()) {
                columnByKey.putIfAbsent(col.getMongoId(), col);
            }
        }

        // 构建站点ID到站点对象的映射
        Map<Long, Site> siteMap = allSites.stream()
                .collect(Collectors.toMap(Site::getId, site -> site));

        // 构建返回的栏目列表，保持 columnStore 中的顺序，去重避免同一栏目被两种 key 重复命中
        List<Map<String, Object>> displayColumns = new ArrayList<>();
        Set<Long> emittedColumnIds = new HashSet<>();
        for (String key : columnStoreKeys) {
            ColumnEntity column = columnByKey.get(key);
            if (column == null) continue;
            if (!emittedColumnIds.add(column.getId())) continue;

            // 通过ColumnSite关联表获取该栏目下的站点
            List<ColumnSite> columnSites = columnSiteRepository.findByColumnId(column.getId());
            List<Long> siteIds = columnSites.stream()
                    .map(cs -> cs.getSite().getId())
                    .collect(Collectors.toList());

            List<Map<String, Object>> sites = new ArrayList<>();

            for (Long siteId : siteIds) {
                Site site = siteMap.get(siteId);
                if (site == null) continue;

                Map<String, Object> siteInfo = new HashMap<>();
                siteInfo.put("id", site.getId());
                siteInfo.put("_id", site.getMongoId());
                siteInfo.put("name", site.getName());
                siteInfo.put("url", site.getUrl());
                siteInfo.put("icon", site.getIcon());
                siteInfo.put("code", site.getCode());
                siteInfo.put("expand", site.getExpand());
                siteInfo.put("description", site.getDescription());
                siteInfo.put("remarks", site.getRemarks());
                siteInfo.put("describe", site.getDescription());
                siteInfo.put("remark", site.getRemarks());
                sites.add(siteInfo);
            }

            Map<String, Object> columnInfo = new HashMap<>();
            columnInfo.put("typeName", column.getName());
            columnInfo.put("sites", sites);
            displayColumns.add(columnInfo);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", journal.getId());
        result.put("_id", journal.getId());
        result.put("name", journal.getName());
        result.put("code", journal.getCode());
        result.put("expand", journal.getExpand());
        result.put("series", displayColumns);

        return result;
    }

    private List<String> parseColumnStoreAsStrings(String columnStore) {
        if (columnStore == null || columnStore.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(columnStore, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<Long> parseColumnStore(String columnStore) {
        if (columnStore == null || columnStore.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(columnStore, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<Long> parseSiteStore(String siteStore) {
        if (siteStore == null || siteStore.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(siteStore, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}

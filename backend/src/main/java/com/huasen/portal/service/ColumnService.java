package com.huasen.portal.service;

import com.huasen.common.entity.ColumnEntity;
import com.huasen.common.entity.ColumnSite;
import com.huasen.common.entity.Site;
import com.huasen.common.exception.BusinessException;
import com.huasen.common.repository.ColumnRepository;
import com.huasen.common.repository.ColumnSiteRepository;
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
 * 栏目服务
 * 对应Node.js: column.controller.js中的业务逻辑
 */
@Service
public class ColumnService {

    @Autowired
    private ColumnRepository columnRepository;

    @Autowired
    private ColumnSiteRepository columnSiteRepository;

    @Autowired
    private SiteRepository siteRepository;

    /**
     * 添加栏目
     * 对应Node.js: column.controller.add
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public ColumnEntity add(Map<String, Object> params) {
        // 前端发送格式: { data: { name: '...', enabled: true, ... } }
        Map<String, Object> data = params;
        if (params.containsKey("data") && params.get("data") instanceof Map) {
            data = (Map<String, Object>) params.get("data");
        }

        System.out.println("DEBUG - 接收到的数据: " + data);
        System.out.println("DEBUG - remarks 值: " + data.get("remarks"));

        ColumnEntity column = new ColumnEntity();
        column.setName((String) data.get("name"));
        column.setDescription((String) data.get("description"));
        column.setIcon((String) data.get("icon"));
        column.setRemarks((String) data.get("remarks"));

        if (data.get("enabled") != null) {
            column.setEnabled((Boolean) data.get("enabled"));
        }
        if (data.get("code") != null) {
            column.setCode(((Number) data.get("code")).intValue());
        }

        System.out.println("DEBUG - 保存前的 column: " + column);
        ColumnEntity saved = columnRepository.save(column);
        System.out.println("DEBUG - 保存后的 column: " + saved);
        return saved;
    }

    /**
     * 删除栏目
     * 对应Node.js: column.controller.remove
     */
    @Transactional
    public void remove(Long id) {
        if (!columnRepository.existsById(id)) {
            throw new BusinessException("ERROR", "栏目不存在");
        }
        // 同时删除关联的column_site记录
        List<ColumnSite> bindings = columnSiteRepository.findByColumnId(id);
        if (!bindings.isEmpty()) {
            columnSiteRepository.deleteAll(bindings);
        }
        columnRepository.deleteById(id);
    }

    /**
     * 更新栏目
     * 对应Node.js: column.controller.update
     */
    @Transactional
    public ColumnEntity update(Long id, Map<String, Object> params) {
        System.out.println("DEBUG UPDATE - 接收到的参数: " + params);
        System.out.println("DEBUG UPDATE - remarks 值: " + params.get("remarks"));

        ColumnEntity column = columnRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ERROR", "栏目不存在"));

        System.out.println("DEBUG UPDATE - 更新前的 column.remarks: " + column.getRemarks());

        if (params.containsKey("name")) {
            column.setName((String) params.get("name"));
        }
        if (params.containsKey("description")) {
            column.setDescription((String) params.get("description"));
        }
        if (params.containsKey("icon")) {
            column.setIcon((String) params.get("icon"));
        }
        if (params.containsKey("remarks")) {
            String remarks = (String) params.get("remarks");
            System.out.println("DEBUG UPDATE - 准备设置 remarks: " + remarks);
            column.setRemarks(remarks);
        }
        if (params.containsKey("enabled")) {
            column.setEnabled((Boolean) params.get("enabled"));
        }
        if (params.containsKey("code")) {
            column.setCode(((Number) params.get("code")).intValue());
        }

        System.out.println("DEBUG UPDATE - 保存前的 column.remarks: " + column.getRemarks());
        ColumnEntity saved = columnRepository.save(column);
        System.out.println("DEBUG UPDATE - 保存后的 column.remarks: " + saved.getRemarks());

        return saved;
    }

    /**
     * 分页查询栏目（管理端）
     * 对应Node.js: column.controller.findAllByPage
     */
    public Map<String, Object> findByPage(int pageNo, int pageSize, String name, Integer code) {
        Pageable pageable = PageRequest.of(Math.max(pageNo - 1, 0), pageSize);
        Page<ColumnEntity> page;

        if (name != null && !name.isEmpty() && code != null) {
            page = columnRepository.findByNameContainingIgnoreCaseAndCode(name, code, pageable);
        } else if (name != null && !name.isEmpty()) {
            page = columnRepository.findByNameContainingIgnoreCase(name, pageable);
        } else if (code != null) {
            page = columnRepository.findByCode(code, pageable);
        } else {
            page = columnRepository.findAll(pageable);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getContent());
        result.put("total", page.getTotalElements());
        return result;
    }

    /**
     * 按权限码查询栏目（用户端）
     * 对应Node.js: column.controller.findByCode
     * 筛选出code <= 用户权限码 且 enabled=true 的栏目
     */
    public List<ColumnEntity> findByCode(Integer userCode) {
        return columnRepository.findByCodeLessThanEqualAndEnabledTrue(userCode);
    }

    /**
     * 查询所有栏目列表（管理端）
     * 对应Node.js: column.controller.findByList
     */
    public List<ColumnEntity> findByList() {
        return columnRepository.findAll();
    }

    /**
     * 绑定站点到栏目
     * 对应Node.js: column.controller.bindSite
     */
    @Transactional
    public void bindSite(List<Long> columnIds, List<Long> siteIds) {
        List<ColumnEntity> columns = columnRepository.findAllById(columnIds);
        List<Site> sites = siteRepository.findAllById(siteIds);

        if (columns.isEmpty() || sites.isEmpty()) {
            throw new BusinessException("ERROR", "数据异常");
        }

        for (ColumnEntity column : columns) {
            List<ColumnSite> existingBindings = columnSiteRepository.findByColumnId(column.getId());
            Set<Long> existingSiteIds = existingBindings.stream()
                    .map(cs -> cs.getSite().getId())
                    .collect(Collectors.toSet());

            List<ColumnSite> newBindings = new ArrayList<>();
            for (Site site : sites) {
                if (!existingSiteIds.contains(site.getId())) {
                    ColumnSite cs = new ColumnSite();
                    cs.setColumn(column);
                    cs.setSite(site);
                    newBindings.add(cs);
                }
            }

            if (!newBindings.isEmpty()) {
                columnSiteRepository.saveAll(newBindings);
            }
        }
    }

    /**
     * 从栏目解绑站点
     * 对应Node.js: column.controller.unbindSite
     */
    @Transactional
    public void unbindSite(List<Long> columnIds, List<Long> siteIds) {
        Set<Long> siteIdSet = new HashSet<>(siteIds);

        for (Long columnId : columnIds) {
            List<ColumnSite> bindings = columnSiteRepository.findByColumnId(columnId);
            List<ColumnSite> toRemove = bindings.stream()
                    .filter(cs -> siteIdSet.contains(cs.getSite().getId()))
                    .collect(Collectors.toList());

            if (!toRemove.isEmpty()) {
                columnSiteRepository.deleteAll(toRemove);
            }
        }
    }
}

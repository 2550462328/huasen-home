package com.huasen.portal.service;

import com.huasen.common.entity.ColumnEntity;
import com.huasen.common.entity.ColumnSite;
import com.huasen.common.entity.Site;
import com.huasen.common.exception.BusinessException;
import com.huasen.common.repository.ColumnRepository;
import com.huasen.common.repository.ColumnSiteRepository;
import com.huasen.common.repository.SiteRepository;
import com.huasen.common.service.QiniuStorageService;
import com.huasen.common.util.ParamUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 站点服务
 * 对应Node.js: site.controller.js中的业务逻辑
 */
@Service
public class SiteService {

    private static final Logger log = LoggerFactory.getLogger(SiteService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private ColumnRepository columnRepository;

    @Autowired
    private ColumnSiteRepository columnSiteRepository;

    /**
     * 七牛云存储服务（可选 Bean — 仅在 qiniu.access-key 配置时存在）。
     * Landmine 5: required=false 兼容缺失场景，配合 null 校验降级。
     */
    @Autowired(required = false)
    private QiniuStorageService qiniuStorageService;

    @Autowired
    private com.huasen.common.service.ai.SiteDescriptionService siteDescriptionService;

    /**
     * 浏览器插件“快速添加”入口：原子创建站点 + 绑定单一栏目，附带
     * 尽力而为的图标下载/七牛上传（失败不回滚主流程，D-13）。
     *
     * <p>所有校验在写库前完成（Landmine 3）；图标块内联在同一 @Transactional 方法体内，
     * 通过广义 try/catch 吸收异常，避免 Spring 自调用代理失效（Landmine 1）+ 防止图标
     * 步骤异常逃出导致整事务回滚（Landmine 4）。
     *
     * @param params 期望键: name(String), url(String), icon(String? 可选), columnId(数字或数字字符串)
     * @return 创建后的站点（包含 icon CDN URL 或空字符串）
     */
    @Transactional
    public Site quickAdd(Map<String, Object> params) {
        // 1. 校验：写库前抛 BusinessException，避免任何半成品。
        String name = (String) params.get("name");
        if (name == null || name.isBlank()) {
            throw new BusinessException("ERROR", "名称不能为空");
        }
        String url = (String) params.get("url");
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new BusinessException("ERROR", "URL必须以http://或https://开头");
        }
        Long columnId = ParamUtil.getLong(params, "columnId", null);
        if (columnId == null) {
            throw new BusinessException("ERROR", "栏目不存在");
        }

        // 2. 栏目预检（D-09）：早失败优于让 ColumnSite save 抛 FK 异常。
        ColumnEntity column = columnRepository.findById(columnId)
                .orElseThrow(() -> new BusinessException("ERROR", "栏目不存在"));

        // 3. 保存站点（取得 id；icon 暂留空，后续按需回填）。
        Site site = new Site();
        site.setName(name);
        site.setUrl(url);
        // enabled / code 字段已有默认值（true / 0），无需显式设置。
        String passedIconUrl = (String) params.get("icon"); // 预览阶段已爬到的真实 favicon URL，或浏览器兜底（D-08）
        String passedDescription = (String) params.get("description"); // 预览阶段 AI 生成、用户可能已编辑
        site = siteRepository.save(site);

        // 4. 单条 ColumnSite 绑定（D-05：直接 new + save，不调用 ColumnService.bindSite）。
        ColumnSite cs = new ColumnSite();
        cs.setColumn(column);
        cs.setSite(site);
        columnSiteRepository.save(cs);

        // 5+6. icon + 描述。插件「预览」(POST /site/preview) 已把真实 favicon URL 和 AI 描述
        // 回传到表单，用户保存时原样带回 —— 此时无需再爬，直接用传入值（用户可能已编辑描述）。
        // 仅当某一项缺失（旧插件/直接调用）才服务端爬一次 HTML 兜底，与后管 fetchFavicons 对齐。
        boolean needIcon = (passedIconUrl == null || passedIconUrl.isBlank());
        boolean needDesc = (passedDescription == null || passedDescription.isBlank());

        String html = "";
        String crawledIconUrl = "";
        if (needIcon || needDesc) {
            // crawlSiteHtmlAndIcon 内部吸收所有异常，绝不抛出，主事务安全（Landmine 4）。
            String[] crawled = crawlSiteHtmlAndIcon(url);
            html = crawled[0];
            crawledIconUrl = crawled[1];
        }

        // 5. 尽力而为的图标处理 — 严禁抛出未捕获异常逃出本方法，否则整事务回滚（D-13 + Landmine 4）。
        // 优先用传入的 icon（预览结果/用户保留），缺失才用服务端爬取的兜底。
        if (qiniuStorageService != null) {
            String chosenIcon = !needIcon ? passedIconUrl : crawledIconUrl;
            if (chosenIcon != null && !chosenIcon.isBlank()) {
                try {
                    byte[] bytes = downloadIconBytes(chosenIcon);
                    String cdn = qiniuStorageService.upload(bytes, "favicon.ico", "icon");
                    site.setIcon(cdn);
                    site = siteRepository.save(site);
                } catch (Exception e) {
                    log.warn("quick-add icon处理失败, 站点仍创建: chosen={}, err={}",
                            chosenIcon, e.getMessage());
                    // 降级：保留 site.icon 为空（D-13）；站点 + 绑定已落库。
                }
            }
        }

        // 6. 描述：传入非空直接用（用户可能已编辑）；缺失才用爬到的 HTML 生成（非阻塞，绝不抛异常）。
        try {
            String description;
            if (!needDesc) {
                description = passedDescription;
            } else {
                description = !html.isBlank()
                        ? siteDescriptionService.generateFromHtml(html)
                        : siteDescriptionService.generateFromUrl(url);
            }
            if (description != null && !description.isEmpty()) {
                site.setDescription(description);
                site = siteRepository.save(site);
            }
        } catch (Exception e) {
            log.warn("quick-add 描述生成失败（非阻塞），站点仍创建: url={}, err={}", url, e.getMessage());
            // 降级：保留 site.description 为空；站点 + 绑定 + 图标已落库。
        }

        return site;
    }

    /**
     * 插件「收藏预览」：爬一次首页 HTML，返回真实 favicon URL + AI 生成的极短描述。
     *
     * <p>供 {@code POST /site/preview} 调用，让插件 popup 在保存前就能展示/编辑描述、
     * 预览真实图标（不再只依赖浏览器 tab.favIconUrl）。与后管「新增网链」预览体验对齐。
     *
     * <p>非阻塞契约：任何环节失败对应字段降级为 ""，绝不抛异常。返回的 icon 是 http(s) URL
     * （popup 用 &lt;img&gt; 直接渲染，保存时原样回传给 quickAdd 下载并上传七牛）。
     *
     * @param url 目标网址（http/https）
     * @return {@code {icon: String, description: String}}，失败字段为 ""
     */
    public Map<String, Object> previewSite(String url) {
        Map<String, Object> result = new HashMap<>();
        result.put("icon", "");
        result.put("description", "");
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return result;
        }
        String[] crawled = crawlSiteHtmlAndIcon(url);
        String html = crawled[0];
        String iconUrl = crawled[1];
        result.put("icon", iconUrl != null ? iconUrl : "");
        try {
            String description = siteDescriptionService.generateFromHtml(html);
            result.put("description", description != null ? description : "");
        } catch (Exception e) {
            log.warn("preview 描述生成失败（非阻塞）, url={}, err={}", url, e.getMessage());
        }
        return result;
    }

    /**
     * 抓站点首页 HTML，顺路从 &lt;link rel="icon"&gt; 解析首个候选图标 URL（无则兜底 /favicon.ico）。
     *
     * <p>后管「新增网链」({@code ManageService.fetchFavicons}) 同款爬取范式，quick-add 复用以
     * 与后管行为对齐：5s 连接 / 10s 请求超时、跟随重定向、CASE_INSENSITIVE 正则。
     *
     * <p>非阻塞契约：所有异常吸收为 warn 日志，返回值的两个字段在失败时均为空字符串。
     * 调用方可安全地夹在 {@code @Transactional} 方法内而不触发回滚。
     *
     * @param url 站点 URL (http/https)
     * @return 长度为 2 的数组 [html, iconUrl]，任一失败/缺失字段为 ""
     */
    private String[] crawlSiteHtmlAndIcon(String url) {
        String html = "";
        String iconUrl = "";
        try {
            URI uri = URI.create(url);
            String baseUrl = uri.getScheme() + "://" + uri.getHost();

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            html = response.body() != null ? response.body() : "";

            // 解析首个 <link rel="icon"> / <link rel="shortcut icon">
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "<link[^>]*rel=[\"'](?:shortcut )?icon[\"'][^>]*href=[\"']([^\"']+)[\"']",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String href = matcher.group(1);
                if (!href.startsWith("http")) {
                    href = href.startsWith("/") ? baseUrl + href : baseUrl + "/" + href;
                }
                iconUrl = href;
            } else {
                // 兜底：默认 /favicon.ico（与 ManageService.fetchFavicons 对齐）
                iconUrl = baseUrl + "/favicon.ico";
            }
        } catch (Exception e) {
            log.warn("quick-add 站点 HTML 爬取失败（非阻塞）, url={}, err={}", url, e.getMessage());
        }
        return new String[]{html, iconUrl};
    }

    /**
     * 下载图标字节，沿用 ManageService.fetchFavicons 的 JDK HttpClient 范式：
     * 5 秒连接超时、5 秒请求超时、跟随重定向，仅在 200 时返回字节，否则抛异常（由调用方捕获降级）。
     */
    private byte[] downloadIconBytes(String iconUrl) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(iconUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new java.io.IOException("icon download non-200: " + resp.statusCode());
        }
        return resp.body();
    }

    /**
     * 添加站点
     * 对应Node.js: site.controller.add
     */
    @Transactional
    public Site add(Map<String, Object> params) {
        Site site = new Site();
        site.setName((String) params.get("name"));
        site.setUrl((String) params.get("url"));
        site.setDescription((String) params.get("description"));
        site.setIcon((String) params.get("icon"));
        site.setRemarks((String) params.get("remarks"));

        if (params.get("enabled") != null) {
            site.setEnabled((Boolean) params.get("enabled"));
        }
        if (params.get("code") != null) {
            site.setCode(((Number) params.get("code")).intValue());
        }
        if (params.get("expand") != null) {
            site.setExpand(expandToString(params.get("expand")));
        }

        return siteRepository.save(site);
    }

    /**
     * 批量添加站点
     * 对应Node.js: site.controller.addMany
     */
    @Transactional
    public List<Site> addMany(List<Map<String, Object>> sites) {
        if (sites == null || sites.isEmpty()) {
            throw new BusinessException("ERROR", "导入数据异常");
        }

        List<Site> siteEntities = sites.stream().map(params -> {
            Site site = new Site();
            site.setName((String) params.get("name"));
            site.setUrl((String) params.get("url"));
            site.setDescription((String) params.get("description"));
            site.setIcon((String) params.get("icon"));
            site.setRemarks((String) params.get("remarks"));
            if (params.get("enabled") != null) {
                site.setEnabled((Boolean) params.get("enabled"));
            }
            if (params.get("code") != null) {
                site.setCode(((Number) params.get("code")).intValue());
            }
            if (params.get("expand") != null) {
                site.setExpand(expandToString(params.get("expand")));
            }
            return site;
        }).collect(Collectors.toList());

        return siteRepository.saveAll(siteEntities);
    }

    /**
     * 删除站点
     * 对应Node.js: site.controller.remove
     */
    @Transactional
    public void remove(Long id) {
        if (!siteRepository.existsById(id)) {
            throw new BusinessException("ERROR", "站点不存在");
        }
        // 同时删除关联的column_site记录
        List<ColumnSite> bindings = columnSiteRepository.findBySiteId(id);
        if (!bindings.isEmpty()) {
            columnSiteRepository.deleteAll(bindings);
        }
        siteRepository.deleteById(id);
    }

    /**
     * 批量删除站点
     * 对应Node.js: site.controller.removeMany
     */
    @Transactional
    public void removeMany(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("ERROR", "参数异常");
        }
        for (Long id : ids) {
            List<ColumnSite> bindings = columnSiteRepository.findBySiteId(id);
            if (!bindings.isEmpty()) {
                columnSiteRepository.deleteAll(bindings);
            }
        }
        siteRepository.deleteAllById(ids);
    }

    /**
     * 更新站点
     * 对应Node.js: site.controller.update
     */
    @Transactional
    public Site update(Long id, Map<String, Object> params) {
        Site site = siteRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ERROR", "站点不存在"));

        if (params.containsKey("name")) {
            site.setName((String) params.get("name"));
        }
        if (params.containsKey("url")) {
            site.setUrl((String) params.get("url"));
        }
        if (params.containsKey("description")) {
            site.setDescription((String) params.get("description"));
        }
        if (params.containsKey("icon")) {
            site.setIcon((String) params.get("icon"));
        }
        if (params.containsKey("remarks")) {
            site.setRemarks((String) params.get("remarks"));
        }
        if (params.containsKey("enabled")) {
            site.setEnabled((Boolean) params.get("enabled"));
        }
        if (params.containsKey("code")) {
            site.setCode(((Number) params.get("code")).intValue());
        }
        if (params.containsKey("expand")) {
            site.setExpand(expandToString(params.get("expand")));
        }

        return siteRepository.save(site);
    }

    /**
     * 分页查询站点（管理端）
     * 对应Node.js: site.controller.findAllByPage
     */
    public Map<String, Object> findByPage(int pageNo, int pageSize, String name, Integer code) {
        Pageable pageable = PageRequest.of(Math.max(pageNo - 1, 0), pageSize);
        Page<Site> page;

        if (name != null && !name.isEmpty() && code != null) {
            page = siteRepository.findByNameContainingIgnoreCaseAndCode(name, code, pageable);
        } else if (name != null && !name.isEmpty()) {
            page = siteRepository.findByNameContainingIgnoreCase(name, pageable);
        } else if (code != null) {
            page = siteRepository.findByCode(code, pageable);
        } else {
            page = siteRepository.findAll(pageable);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getContent());
        result.put("total", page.getTotalElements());
        return result;
    }

    /**
     * 按权限码查询站点（用户端）
     * 对应Node.js: site.controller.findByCode
     * 筛选出code <= 用户权限码 且 enabled=true 的站点
     */
    public List<Site> findByCode(Integer userCode) {
        return siteRepository.findByCodeLessThanEqualAndEnabledTrue(userCode);
    }

    /**
     * 查询所有站点列表（管理端）
     * 对应Node.js: site.controller.findByList
     */
    public List<Site> findByList() {
        return siteRepository.findAll();
    }

    /**
     * 查询站点标签列表
     * 对应Node.js: site.controller.findSiteTagByList
     */
    public List<String> findSiteTagByList() {
        List<Site> sites = siteRepository.findAll();
        Set<String> tags = new LinkedHashSet<>();

        for (Site site : sites) {
            Map<String, Object> expand = parseExpand(site.getExpand());
            if (expand == null) continue;
            Object tagObj = expand.get("tag");
            if (tagObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> tagList = (List<String>) tagObj;
                tags.addAll(tagList);
            }
        }

        return new ArrayList<>(tags);
    }

    /**
     * 查询站点所属栏目
     * 对应Node.js: site.controller.findSiteColumnByList
     */
    public List<Long> findSiteColumnByList(Long siteId) {
        List<ColumnSite> bindings = columnSiteRepository.findBySiteId(siteId);
        return bindings.stream()
                .map(cs -> cs.getColumn().getId())
                .collect(Collectors.toList());
    }

    /**
     * 站点绑定栏目
     * 对应Node.js: site.controller.bindColumn
     * 将指定站点绑定到指定栏目
     */
    @Transactional
    public void bindColumn(Long columnId, List<Long> siteIds) {
        ColumnEntity column = columnRepository.findById(columnId)
                .orElseThrow(() -> new BusinessException("ERROR", "栏目不存在"));

        List<ColumnSite> existingBindings = columnSiteRepository.findByColumnId(columnId);
        Set<Long> existingSiteIds = existingBindings.stream()
                .map(cs -> cs.getSite().getId())
                .collect(Collectors.toSet());

        List<Site> sites = siteRepository.findAllById(siteIds);
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

    /**
     * 站点解绑栏目
     * 对应Node.js: site.controller.unbindColumn
     * 将指定站点从指定栏目解绑
     */
    @Transactional
    public void unbindColumn(Long columnId, List<Long> siteIds) {
        List<ColumnSite> bindings = columnSiteRepository.findByColumnId(columnId);
        Set<Long> siteIdSet = new HashSet<>(siteIds);

        List<ColumnSite> toRemove = bindings.stream()
                .filter(cs -> siteIdSet.contains(cs.getSite().getId()))
                .collect(Collectors.toList());

        if (!toRemove.isEmpty()) {
            columnSiteRepository.deleteAll(toRemove);
        }
    }

    /**
     * 将前端传来的 expand 统一转换为 JSON 字符串存储。
     * 前端正常发送 JSON 字符串；若历史/异常情况下传来 Map，则序列化为字符串。
     */
    private String expandToString(Object expandObj) {
        if (expandObj == null) {
            return null;
        }
        if (expandObj instanceof String) {
            return (String) expandObj;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(expandObj);
        } catch (JsonProcessingException e) {
            throw new BusinessException("ERROR", "拓展字段序列化失败");
        }
    }

    /**
     * 将存储的 expand JSON 字符串解析为 Map，供服务端读取（如标签聚合）。
     * 解析失败或为空时返回 null。
     */
    private Map<String, Object> parseExpand(String expand) {
        if (expand == null || expand.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(expand, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}

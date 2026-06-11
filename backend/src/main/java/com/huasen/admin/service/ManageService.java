package com.huasen.admin.service;

import com.huasen.common.entity.Manage;
import com.huasen.common.exception.BusinessException;
import com.huasen.common.repository.ManageRepository;
import com.huasen.common.repository.UserRepository;
import com.huasen.common.repository.ArticleRepository;
import com.huasen.common.service.RedisService;
import com.huasen.common.util.AesUtil;
import com.huasen.common.util.JwtUtil;
import com.huasen.common.util.SystemMonitorUtil;
import com.huasen.common.constant.RedisKeyConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ManageService {

    @Autowired
    private ManageRepository manageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private RedisService redisService;

    @Autowired
    private com.huasen.common.service.ai.SiteDescriptionService siteDescriptionService;

    @Autowired
    private AesUtil aesUtil;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${app.upload.base-path:huasen-store}")
    private String uploadBasePath;

    public Map<String, Object> login(String accountId, String password) {
        Manage manage = manageRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessException("ERROR", "账户不存在"));

        String decryptedPassword = aesUtil.decrypt(manage.getPassword());
        if (!decryptedPassword.equals(password)) {
            throw new BusinessException("ERROR", "账户密码不匹配");
        }

        String token = jwtUtil.createToken(accountId, manage.getCode());

        Map<String, Object> data = new HashMap<>();
        data.put("id", accountId);
        data.put("token", token);
        data.put("code", manage.getCode());
        return data;
    }

    public void add(String accountId, String password, String name, Integer code) {
        if (manageRepository.existsByAccountId(accountId)) {
            throw new BusinessException("ERROR", "管理员已存在");
        }

        String encryptedPassword = aesUtil.encrypt(password);

        Manage manage = new Manage();
        manage.setAccountId(accountId);
        manage.setPassword(encryptedPassword);
        manage.setName(name);
        // 单一超管模型：所有后台管理员统一 code=3，忽略传入的 code，
        // 以通过后端全部写操作的 code>=3 权限闸门。
        manage.setCode(3);
        manage.setEnabled(true);

        manageRepository.save(manage);
    }

    public Map<String, Object> findByPage(int pageNo, int pageSize) {
        Page<Manage> page = manageRepository.findAll(
                PageRequest.of(Math.max(0, pageNo - 1), pageSize));

        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getContent());
        result.put("total", page.getTotalElements());
        return result;
    }

    public void remove(Long id) {
        if (!manageRepository.existsById(id)) {
            throw new BusinessException("ERROR", "管理员不存在");
        }
        manageRepository.deleteById(id);
    }

    public void update(Long id, String accountId, String password, String name,
                       Integer code, Integer jwtCode, String jwtKey) {
        Manage manage = manageRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ERROR", "更新账户不存在"));

        if (jwtCode != 3) {
            if (!manage.getAccountId().equals(jwtKey)) {
                throw new BusinessException("ERROR", "禁止更新其他管理员信息");
            }
            if (code != null && !code.equals(manage.getCode())) {
                throw new BusinessException("ERROR", "禁止修改权限码");
            }
        }

        if (password != null && !password.equals(manage.getPassword())) {
            manage.setPassword(aesUtil.encrypt(password));
        }
        if (name != null) {
            manage.setName(name);
        }
        if (code != null && jwtCode == 3) {
            manage.setCode(code);
        }

        manageRepository.save(manage);
    }

    public Map<String, Object> getOverview() {
        long userCount = userRepository.count();
        long manageCount = manageRepository.count();
        long articleCount = articleRepository.count();
        long fileCount = SystemMonitorUtil.countFilesInDirectory(uploadBasePath);

        String historyJson = redisService.get("STATS_HISTORY");
        long prevUser = 0, prevManage = 0, prevArticle = 0, prevFile = 0;
        if (historyJson != null) {
            try {
                String[] parts = historyJson.split(",");
                if (parts.length >= 4) {
                    prevUser = Long.parseLong(parts[0]);
                    prevManage = Long.parseLong(parts[1]);
                    prevArticle = Long.parseLong(parts[2]);
                    prevFile = Long.parseLong(parts[3]);
                }
            } catch (NumberFormatException ignored) {}
        }

        redisService.set("STATS_HISTORY", userCount + "," + manageCount + "," + articleCount + "," + fileCount);

        Map<String, Object> result = new HashMap<>();
        result.put("userCount", userCount);
        result.put("userRate", SystemMonitorUtil.calculateRate(userCount, prevUser));
        result.put("manageCount", manageCount);
        result.put("manageRate", SystemMonitorUtil.calculateRate(manageCount, prevManage));
        result.put("articleCount", articleCount);
        result.put("articleRate", SystemMonitorUtil.calculateRate(articleCount, prevArticle));
        result.put("fileCount", fileCount);
        result.put("fileRate", SystemMonitorUtil.calculateRate(fileCount, prevFile));
        return result;
    }

    public Map<String, Object> getDiskOverview() {
        return SystemMonitorUtil.getDiskInfo();
    }

    public Map<String, Object> getVisitor() {
        Map<Object, Object> accessMap = redisService.hgetall(RedisKeyConstants.POOL_ACCESS);
        long visitorCount = accessMap != null ? accessMap.size() : 0;

        String prevStr = redisService.get("VISITOR_HISTORY");
        long prevCount = 0;
        if (prevStr != null) {
            try {
                prevCount = Long.parseLong(prevStr);
            } catch (NumberFormatException ignored) {}
        }

        redisService.set("VISITOR_HISTORY", String.valueOf(visitorCount));

        Map<String, Object> result = new HashMap<>();
        result.put("visitorCount", visitorCount);
        result.put("visitorRate", SystemMonitorUtil.calculateRate(visitorCount, prevCount));
        return result;
    }

    public Map<String, Object> fetchFavicons(String url) {
        List<String> icons = new ArrayList<>();
        String description = "";
        try {
            java.net.URI uri = java.net.URI.create(url);
            String baseUrl = uri.getScheme() + "://" + uri.getHost();

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(java.time.Duration.ofSeconds(10))
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            String html = response.body();

            // 顺路提取 title/meta（同一份 HTML，零额外网络请求），生成网站描述。
            // 复用 SiteDescriptionService.generateFromHtml（与插件 quick-add 同一套 AI 能力）。
            // 描述生成是非阻塞的：提取失败或 AI 失败时 description 保持 ""，绝不影响图标流程。
            description = siteDescriptionService.generateFromHtml(html);

            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "<link[^>]*rel=[\"'](?:shortcut )?icon[\"'][^>]*href=[\"']([^\"']+)[\"']",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(html);

            int count = 0;
            while (matcher.find() && count < 5) {
                String href = matcher.group(1);
                if (!href.startsWith("http")) {
                    href = href.startsWith("/") ? baseUrl + href : baseUrl + "/" + href;
                }

                try {
                    java.net.http.HttpRequest iconReq = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(href))
                            .timeout(java.time.Duration.ofSeconds(5))
                            .GET()
                            .build();
                    java.net.http.HttpResponse<byte[]> iconResp = client.send(iconReq,
                            java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                    if (iconResp.statusCode() == 200) {
                        String contentType = iconResp.headers().firstValue("Content-Type")
                                .orElse("image/x-icon");
                        String base64 = Base64.getEncoder().encodeToString(iconResp.body());
                        icons.add("data:" + contentType + ";base64," + base64);
                        count++;
                    }
                } catch (Exception ignored) {}
            }

            if (icons.isEmpty()) {
                String defaultFavicon = baseUrl + "/favicon.ico";
                java.net.http.HttpRequest faviconReq = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(defaultFavicon))
                        .timeout(java.time.Duration.ofSeconds(5))
                        .GET()
                        .build();
                java.net.http.HttpResponse<byte[]> faviconResp = client.send(faviconReq,
                        java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                if (faviconResp.statusCode() == 200) {
                    String contentType = faviconResp.headers().firstValue("Content-Type")
                            .orElse("image/x-icon");
                    String base64 = Base64.getEncoder().encodeToString(faviconResp.body());
                    icons.add("data:" + contentType + ";base64," + base64);
                }
            }
        } catch (Exception ignored) {}

        Map<String, Object> result = new HashMap<>();
        result.put("icons", icons);
        result.put("description", description);
        return result;
    }
}

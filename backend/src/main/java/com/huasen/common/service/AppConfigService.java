package com.huasen.common.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huasen.common.entity.SystemConfig;
import com.huasen.common.repository.SystemConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AppConfigService {

    private static final String CONFIG_KEY = "system_config";

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "mail", "jwt", "jwtLiveTime"
    );

    /**
     * 加载完整配置（包含敏感信息）
     */
    public Map<String, Object> loadConfig() {
        Optional<SystemConfig> configOpt = systemConfigRepository.findByConfigKey(CONFIG_KEY);

        if (configOpt.isPresent()) {
            try {
                return objectMapper.readValue(
                    configOpt.get().getConfigValue(),
                    new TypeReference<Map<String, Object>>() {}
                );
            } catch (Exception e) {
                e.printStackTrace();
                return new HashMap<>();
            }
        }

        return new HashMap<>();
    }

    /**
     * 加载配置（移除敏感信息）
     */
    public Map<String, Object> loadConfigForUser() {
        Map<String, Object> config = loadConfig();
        return removeSensitiveFields(config);
    }

    /**
     * 保存配置
     */
    @Transactional
    public boolean saveConfig(Map<String, Object> config) {
        try {
            String configJson = objectMapper.writeValueAsString(config);

            Optional<SystemConfig> existingOpt = systemConfigRepository.findByConfigKey(CONFIG_KEY);

            SystemConfig systemConfig;
            if (existingOpt.isPresent()) {
                systemConfig = existingOpt.get();
                systemConfig.setConfigValue(configJson);
            } else {
                systemConfig = new SystemConfig();
                systemConfig.setConfigKey(CONFIG_KEY);
                systemConfig.setConfigValue(configJson);
                systemConfig.setDescription("系统配置（品牌、主题、文章ID等）");
            }

            systemConfigRepository.save(systemConfig);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 移除敏感字段
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> removeSensitiveFields(Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>(map);
        result.keySet().removeIf(SENSITIVE_KEYS::contains);

        for (Map.Entry<String, Object> entry : result.entrySet()) {
            if (entry.getValue() instanceof Map) {
                Map<String, Object> nested = (Map<String, Object>) entry.getValue();
                Map<String, Object> cleaned = new HashMap<>(nested);
                cleaned.keySet().removeIf(SENSITIVE_KEYS::contains);
                entry.setValue(cleaned);
            }
        }
        return result;
    }
}

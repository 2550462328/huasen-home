package com.huasen.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 处理静态资源映射
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射 /huasen-store 到文件系统中的静态资源目录
        // 开发环境：映射到 origin/huasenjio-compose/huasen-store
        // 生产环境：映射到 /app/huasen-store（Docker 容器中）
        String location = System.getProperty("huasen.store.path",
            "file:./origin/huasenjio-compose/huasen-store/");

        registry.addResourceHandler("/huasen-store/**")
                .addResourceLocations(location);
    }
}

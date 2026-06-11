package com.huasen.common.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class FileUploadService {

    @Value("${app.upload.base-path:huasen-store}")
    private String basePath;

    @Autowired(required = false)
    private QiniuStorageService qiniuStorageService;

    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/jpg"
    );

    private static final Set<String> IMAGE_TYPES = Set.of("icon", "banner", "article", "img");

    public List<Map<String, String>> upload(MultipartFile[] files, String type) throws IOException {
        List<Map<String, String>> results = new ArrayList<>();

        String subDir = switch (type) {
            case "icon" -> "icon";
            case "banner" -> "banner";
            case "article" -> "article";
            case "img" -> "img";
            default -> "default";
        };

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            if (IMAGE_TYPES.contains(type)) {
                String contentType = file.getContentType();
                if (contentType == null || !IMAGE_MIME_TYPES.contains(contentType)) {
                    continue;
                }
            }

            // Route to Qiniu when configured, otherwise use local storage
            if (qiniuStorageService != null) {
                String cdnUrl = qiniuStorageService.upload(file, subDir);
                Map<String, String> fileInfo = new HashMap<>();
                fileInfo.put("path", cdnUrl);
                results.add(fileInfo);
            } else {
                Path targetDir = Paths.get(basePath, subDir);
                Files.createDirectories(targetDir);

                String originalFilename = file.getOriginalFilename();
                String extension = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                }

                String newFilename = System.currentTimeMillis() + extension;
                Path targetFile = targetDir.resolve(newFilename);
                file.transferTo(targetFile.toFile());

                Map<String, String> fileInfo = new HashMap<>();
                fileInfo.put("path", subDir + "/" + newFilename);
                results.add(fileInfo);
            }
        }

        return results;
    }
}

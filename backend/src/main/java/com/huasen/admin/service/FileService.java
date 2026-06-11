package com.huasen.admin.service;

import com.huasen.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文件管理服务
 * 对应Node.js: file.controller.js中的业务逻辑
 */
@Service
public class FileService {

    @Value("${huasen.upload.path:./uploads}")
    private String uploadBasePath;

    @Value("${huasen.icon-source-path:origin/huasenjio-compose}")
    private String iconSourcePath;

    private static final List<String> FILE_TYPES = Arrays.asList(
            "png", "jpg", "jpeg", "zip", "rar", "pdf", "md", "doc", "docx",
            "xls", "xlsx", "ppt", "pptx", "html", "css", "js"
    );

    /**
     * 查询所有文件
     * 对应Node.js: file.controller.findAll
     * 读取上传目录下的所有文件
     */
    public List<String> findAll() {
        Path storePath = Paths.get(uploadBasePath).toAbsolutePath();
        return readDirectory(storePath);
    }

    /**
     * 查询所有图标文件
     * 对应Node.js: file.controller.findAllIcon
     * 读取 huasen-store/icon 目录,返回相对路径列表
     */
    public List<String> findAllIcons() {
        Path iconDir = Paths.get(iconSourcePath, "huasen-store", "icon").toAbsolutePath();
        Path baseDir = Paths.get(iconSourcePath).toAbsolutePath();
        return readDirectoryRelativeTo(iconDir, baseDir);
    }

    /**
     * 删除文件
     * 对应Node.js: file.controller.remove
     * 支持单个或批量删除
     */
    public void remove(String filePath, List<String> filePaths, Boolean isMultiple) {
        try {
            if (Boolean.TRUE.equals(isMultiple) && filePaths != null) {
                // 批量删除
                for (String fp : filePaths) {
                    Path removeFilePath = Paths.get(uploadBasePath).getParent().resolve(fp);
                    Files.deleteIfExists(removeFilePath);
                }
            } else if (filePath != null) {
                // 删除单个文件
                Path removeFilePath = Paths.get(uploadBasePath).getParent().resolve(filePath);
                Files.deleteIfExists(removeFilePath);
            }
        } catch (IOException e) {
            throw new BusinessException("ERROR", "文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 压缩并下载存储目录
     * 对应Node.js: file.controller.downloadStoreByZip
     * 注: 简化实现,实际压缩功能需要额外的库支持
     */
    public String downloadStoreByZip() {
        // 简化实现: 返回存储路径
        // 实际生产环境需要使用zip库进行压缩
        Path storePath = Paths.get(uploadBasePath).toAbsolutePath();
        return storePath.toString();
    }

    /**
     * 上传文件
     * 新增方法用于文件上传
     */
    public String upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("ERROR", "文件为空");
        }

        try {
            // 确保上传目录存在
            Path uploadDir = Paths.get(uploadBasePath).toAbsolutePath();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = UUID.randomUUID().toString() + extension;

            // 保存文件
            Path targetPath = uploadDir.resolve(filename);
            file.transferTo(targetPath.toFile());

            // 返回相对路径
            return "huasen-store/" + filename;
        } catch (IOException e) {
            throw new BusinessException("ERROR", "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 递归读取目录下的所有文件
     * 对应Node.js: utils/tool.js中的readDirectory
     */
    private List<String> readDirectory(Path dirPath) {
        Path basePath = Paths.get(uploadBasePath).toAbsolutePath().getParent();
        return readDirectoryRelativeTo(dirPath, basePath);
    }

    private List<String> readDirectoryRelativeTo(Path dirPath, Path basePath) {
        List<String> files = new ArrayList<>();

        if (!Files.exists(dirPath)) {
            return files;
        }

        try {
            Files.walkFileTree(dirPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString();
                    String ext = getFileExtension(fileName).toLowerCase();

                    if (FILE_TYPES.contains(ext)) {
                        Path relativePath = basePath.relativize(file);
                        files.add(relativePath.toString().replace("\\", "/"));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new BusinessException("ERROR", "读取目录失败: " + e.getMessage());
        }

        return files;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}

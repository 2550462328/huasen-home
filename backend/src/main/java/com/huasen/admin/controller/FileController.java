package com.huasen.admin.controller;

import com.huasen.common.dto.HuasenResponse;
import com.huasen.admin.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件管理控制器
 * 对应Node.js: file.router.js + file.controller.js
 * 路径: /file/*
 */
@RestController
@RequestMapping("/file")
public class FileController {

    @Autowired
    private FileService fileService;

    /**
     * 查询所有文件
     * POST /file/findAll
     * 对应Node.js: router.get('/findAll', handleJWT, checkManagePower, findAll)
     * 注: 统一改为POST
     */
    @PostMapping("/findAll")
    public ResponseEntity<HuasenResponse> findAll(HttpServletRequest request) {
        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        List<String> files = fileService.findAll();
        return HuasenResponse.success(files, "文件查询成功");
    }

    /**
     * 查询所有图标文件
     * POST /file/findAllIcon
     * 对应Node.js: router.get('/findAllIcon', handleJWT, checkManagePower, findAllIcon)
     * 注: 统一改为POST
     */
    @PostMapping("/findAllIcon")
    public ResponseEntity<HuasenResponse> findAllIcon(HttpServletRequest request) {
        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        List<String> icons = fileService.findAllIcons();
        return HuasenResponse.success(icons, "初始化图标库成功");
    }

    /**
     * 删除文件
     * POST /file/remove
     * 对应Node.js: router.post('/remove', handleJWT, checkManagePower, remove)
     */
    @PostMapping("/remove")
    public ResponseEntity<HuasenResponse> remove(
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        String filePath = (String) params.get("filePath");
        @SuppressWarnings("unchecked")
        List<String> filePaths = (List<String>) params.get("filePaths");
        Boolean isMultiple = (Boolean) params.get("isMultiple");

        fileService.remove(filePath, filePaths, isMultiple);
        return HuasenResponse.success(new HashMap<>(), "文件删除成功");
    }

    /**
     * 压缩并下载存储目录
     * POST /file/downloadStoreByZip
     * 对应Node.js: router.post('/downloadStoreByZip', handleJWT, checkManagePower, downloadStoreByZip)
     */
    @PostMapping("/downloadStoreByZip")
    public ResponseEntity<HuasenResponse> downloadStoreByZip(HttpServletRequest request) {
        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        String zipFilePath = fileService.downloadStoreByZip();
        Map<String, Object> result = new HashMap<>();
        result.put("filePath", zipFilePath);
        return HuasenResponse.success(result, "查询文件句柄成功");
    }

    /**
     * 上传文件
     * POST /file/upload
     * 新增接口用于文件上传
     */
    @PostMapping("/upload")
    public ResponseEntity<HuasenResponse> upload(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        Integer code = (Integer) request.getAttribute("huasenJWT_code");
        if (code == null || code < 3) {
            return HuasenResponse.forbidden("权限不足");
        }

        String filePath = fileService.upload(file);
        Map<String, Object> result = new HashMap<>();
        result.put("filePath", filePath);
        return HuasenResponse.success(result, "文件上传成功");
    }
}

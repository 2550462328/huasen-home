package scripts;

import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * 图标批量上传工具 - 独立命令行工具
 *
 * 使用方法:
 * cd backend
 * javac -cp "target/classes:~/.m2/repository/com/qiniu/qiniu-java-sdk/7.13.0/qiniu-java-sdk-7.13.0.jar:~/.m2/repository/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar" scripts/IconBatchUploader.java
 * java -cp ".:target/classes:~/.m2/repository/com/qiniu/qiniu-java-sdk/7.13.0/qiniu-java-sdk-7.13.0.jar:~/.m2/repository/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar" scripts.IconBatchUploader
 *
 * @author huizhang43
 * @date 2026-06-05
 */
public class IconBatchUploader {

    // 七牛云配置 - 从环境变量读取
    private static final String ACCESS_KEY = System.getenv().getOrDefault("QINIU_ACCESS_KEY", "");
    private static final String SECRET_KEY = System.getenv().getOrDefault("QINIU_SECRET_KEY", "");
    private static final String BUCKET = System.getenv().getOrDefault("QINIU_BUCKET", "");
    private static final String DOMAIN = System.getenv().getOrDefault("QINIU_DOMAIN", "");

    // 文件路径配置
    private static final String ICON_SOURCE_DIR = "deploy/huasen-store/icon";
    private static final String SQL_OUTPUT_FILE = "deploy/icon-migration.sql";

    public static void main(String[] args) {
        try {
            IconBatchUploader uploader = new IconBatchUploader();
            uploader.run();
        } catch (Exception e) {
            System.err.println("上传失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void run() throws IOException {
        System.out.println("===== 图标批量上传工具 =====");
        System.out.println("源目录: " + ICON_SOURCE_DIR);
        System.out.println("SQL 输出: " + SQL_OUTPUT_FILE);
        System.out.println();

        // 1. 扫描本地图标文件
        File iconDir = new File(ICON_SOURCE_DIR);

package com.huasen.common.util;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.sun.management.OperatingSystemMXBean;

public final class SystemMonitorUtil {

    private SystemMonitorUtil() {}

    public static Map<String, Object> getDiskInfo() {
        Map<String, Object> info = new HashMap<>();
        File root = File.listRoots().length > 0 ? File.listRoots()[0] : new File("/");

        long total = root.getTotalSpace();
        long free = root.getUsableSpace();
        long used = total - free;

        String diskName = root.getAbsolutePath().length() <= 3 ? root.getAbsolutePath() : "根目录";

        info.put("diskName", diskName);
        info.put("freeValue", formatSize(free));
        info.put("totalValue", formatSize(total));
        info.put("useValue", formatSize(used));
        info.put("useUsage", total > 0 ? Math.round((double) used / total * 10000.0) / 100.0 : 0.0);

        return info;
    }

    public static Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        info.put("freeMemory", formatSize(osBean.getFreeMemorySize()));
        info.put("totalMemory", formatSize(osBean.getTotalMemorySize()));
        info.put("cpuUsage", Math.round(osBean.getCpuLoad() * 10000.0) / 100.0);

        return info;
    }

    public static long countFilesInDirectory(String path) {
        Path dir = Paths.get(path);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            return 0;
        }
    }

    public static String calculateRate(long current, long previous) {
        if (previous == 0) {
            return "0%";
        }
        long diff = current - previous;
        double rate = (double) diff / previous * 100;
        long rounded = Math.round(rate);
        return (rounded >= 0 ? "+" : "") + rounded + "%";
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format("%.2fGB", bytes / (1024.0 * 1024 * 1024));
        } else if (bytes >= 1024L * 1024) {
            return String.format("%.2fMB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2fKB", bytes / 1024.0);
        }
    }
}

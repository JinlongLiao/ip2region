package org.lionsoul.ip2region.maintenance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** 维护产物采用同目录临时文件与原子替换，失败不覆盖已有数据。 */
public final class MaintenanceFiles {
    /** 只提供静态文件发布操作。 */
    private MaintenanceFiles() {
    }

    /**
     * 原子发布已完成的候选文件；文件系统不支持原子替换时直接失败，保留原文件。
     * @param candidate 同目录的完整候选文件
     * @param destination 最终产物路径
     * @throws IOException 原子替换失败
     */
    public static void publishMaintenanceFile(Path candidate, Path destination) throws IOException {
        Files.move(candidate, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 先写临时文件，写入完成后发布；即使编码或磁盘失败也不截断已有输出。
     * @param destination 输出文件，父目录必须存在
     * @param lines 已校验完成的维护记录
     * @throws IOException 临时写入或发布失败
     */
    public static void writeMaintenanceLines(Path destination, List<String> lines) throws IOException {
        Path output = destination.toAbsolutePath().normalize();
        Path candidate = Files.createTempFile(output.getParent(), "ipv4-maintenance-", ".tmp");
        try {
            Files.write(candidate, lines, StandardCharsets.UTF_8);
            publishMaintenanceFile(candidate, output);
        } finally {
            Files.deleteIfExists(candidate);
        }
    }
}

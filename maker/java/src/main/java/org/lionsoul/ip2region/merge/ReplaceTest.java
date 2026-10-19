package org.lionsoul.ip2region.merge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.lionsoul.ip2region.maintenance.MaintenanceFiles;

/** IPv4 省市名称清理工具；仅清理新版省、市两列的末尾“省”和“市”。 */
public class ReplaceTest {

    /**
     * 在写出前完成所有行的七列校验；按项目规则修正国家与代码，保留 ISP，不修改输入原件。
     * @param args 输入、独立输出路径及可选源格式声明
     * @throws IOException 文件读取或写入失败
     * @throws IllegalArgumentException 输入输出相同或字段数量错误
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException("IPv4 clean: source output --source-format=latest|legacy");
        }
        String sourceFormat = "latest";
        if (args.length == 3) {
            if (!args[2].startsWith("--source-format=")) {
                throw new IllegalArgumentException("expected --source-format=latest|legacy");
            }
            sourceFormat = args[2].substring("--source-format=".length());
        }
        if (!"latest".equals(sourceFormat) && !"legacy".equals(sourceFormat)) {
            throw new IllegalArgumentException("explicit --source-format=latest|legacy is required");
        }
        Path input = Paths.get(args[0]).toRealPath();
        Path output = Paths.get(args[1]).toAbsolutePath().normalize();
        if (input.equals(output) || (Files.exists(output) && Files.isSameFile(input, output))) {
            throw new IllegalArgumentException("IPv4 cleanup output must differ from input");
        }
        List<String> cleaned = new ArrayList<>();
        for (String line : Files.readAllLines(input, StandardCharsets.UTF_8)) {
            RegionItem region = new RegionItem(line, sourceFormat);
            String province = region.getProvince().replaceFirst("省$", "");
            String city = region.getCity().replaceFirst("市$", "");
            cleaned.add(IpConveter.longToIp(region.getStart()) + "|" + IpConveter.longToIp(region.getEnd())
                    + "|" + region.getCountry() + "|" + province + "|" + city + "|" + region.getIsp()
                    + "|" + region.getCode());
        }
        MaintenanceFiles.writeMaintenanceLines(output, cleaned);
    }
}

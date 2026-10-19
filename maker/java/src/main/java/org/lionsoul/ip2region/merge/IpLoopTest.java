package org.lionsoul.ip2region.merge;

import com.wlzn.common.util.common.StringUtil;

import org.lionsoul.ip2region.maintenance.MaintenanceFiles;
import org.lionsoul.ip2region.merge.ip.BaiduQifuIpHelper;
import org.lionsoul.ip2region.merge.ip.IpHelper;
import org.lionsoul.ip2region.merge.ip.RateLimitedRoundRobinIpHelper;
import org.lionsoul.ip2region.merge.ip.pojo.AreaIsp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Objects;

/**
 * 对明确指定的 IPv4 范围每隔 256 个地址采集一次，生成独立的补丁 CSV。
 * 沿用实际代码收集所有非空归属的行为，不额外增加省份过滤；不修改 classpath 或 jar 内资源。
 */
public class IpLoopTest {
    /**
     * 无参数运行时的起始 IPv4；按原项目方式直接在源码维护候选范围。
     */
    private static final String DEFAULT_START_IP = "36.6.27.0";
    /**
     * 无参数运行时包含的结束 IPv4，保持原项目范围不变。
     */
    private static final String DEFAULT_END_IP = "36.192.160.255";
    /**
     * 默认外部补丁文件，相对当前工作目录；不写入 classpath 或 jar 资源。
     */
    private static final Path DEFAULT_PATCH_OUTPUT = Paths.get("merget.txt");

    /**
     * 查询器保持原有 50 毫秒间隔；仅主入口使用，测试通过方法参数传入本地桩。
     */
    static IpHelper ipHelper = new RateLimitedRoundRobinIpHelper(
            Collections.singletonList(new BaiduQifuIpHelper()), 50L);

    /**
     * 无参数时按源码范围采集；也可用三个参数临时覆盖范围与外部输出。
     *
     * @param args 空数组使用源码配置，或起始 IPv4、结束 IPv4、输出 CSV 路径
     * @throws IOException 文件写入失败
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            collectIpv4PatchSamples(DEFAULT_START_IP, DEFAULT_END_IP, DEFAULT_PATCH_OUTPUT, ipHelper);
            return;
        }
        if (args.length != 3) {
            throw new IllegalArgumentException("IPv4 collect: no args for source configuration, or start-ip end-ip output.csv");
        }
        collectIpv4PatchSamples(args[0], args[1], Paths.get(args[2]), ipHelper);
    }

    /**
     * 先校验范围与输出目录，再采集到临时文件；成功完成后才原子替换结果。
     * 样本查询返回空时跳过；异常或中断时停止，不发布半份结果。
     * 使用 long 步进，确保 255.255.255.255 之后不会回绕到 0.0.0.0。
     *
     * @param startAddress 起始样本 IPv4
     * @param endAddress   包含的结束 IPv4
     * @param output       输出补丁文件，不再写入 merget.txt 资源
     * @param ipv4IpHelper 供应方查询器或测试桩，不能为空
     * @throws IOException 文件写入或采集被中断
     */
    public static void collectIpv4PatchSamples(String startAddress, String endAddress, Path output,
                                               IpHelper ipv4IpHelper) throws IOException {
        long start = IpConveter.ipToLong(startAddress);
        long end = IpConveter.ipToLong(endAddress);
        if (start > end || Objects.isNull(ipv4IpHelper)) {
            throw new IllegalArgumentException("invalid IPv4 collection range or helper");
        }
        Path destination = output.toAbsolutePath().normalize();
        Path candidate = Files.createTempFile(destination.getParent(), "ipv4-collect-", ".csv");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(candidate, StandardCharsets.UTF_8)) {
                for (long address = start; address <= end; address += 256L) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("IPv4 collection interrupted at " + IpConveter.longToIp(address));
                    }
                    String ip = IpConveter.longToIp(address);
                    AreaIsp areaIsp = ipv4IpHelper.queryIp(ip);
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("IPv4 collection interrupted after query " + ip);
                    }
                    if (Objects.isNull(areaIsp)) {
                        continue;
                    }
                    String country = Ipv4CountryNormalizer.normalizeIpv4Country(areaIsp.getCountry());
                    String province = StringUtil.isBlank(areaIsp.getProvince()) ? "0" : areaIsp.getProvince();
                    String city = StringUtil.isBlank(areaIsp.getCity()) ? "0" : areaIsp.getCity();
                    String isp = StringUtil.isBlank(areaIsp.getIsp()) ? "0" : areaIsp.getIsp();
                    String[] fields = {ip, "0", "0", "0", country, province, city, isp};
                    for (String field : fields) {
                        if (field.contains(",") || field.contains("\n") || field.contains("\r")) {
                            throw new IllegalArgumentException("invalid CSV field for IPv4 " + ip + ": " + field);
                        }
                    }
                    writer.write(String.join(",", fields));
                    writer.newLine();
                }
            }
            MaintenanceFiles.publishMaintenanceFile(candidate, destination);
        } finally {
            Files.deleteIfExists(candidate);
        }
    }

    /**
     * 比较历史 int 表示的无符号 IPv4，供已有调用方兼容使用。
     *
     * @param startIp 当前地址
     * @param endIp   结束地址
     * @return 当前地址未越过终点
     */
    static boolean isLegalIp(int startIp, int endIp) {
        return Integer.toUnsignedLong(startIp) <= Integer.toUnsignedLong(endIp);
    }

    /**
     * 兼容旧转换入口，按 /24 数量推进并拒绝溢出，不再重复实现四段解析。
     *
     * @param ipString 点分 IPv4
     * @param add      推进的 /24 数量，必须非负
     * @return 合法地址的 32 位表示
     * @throws IllegalArgumentException 输入非法或推进后越界
     */
    public static int ip2Int(String ipString, int add) {
        long address = IpConveter.ipToLong(ipString) + add * 256L;
        if (add < 0 || address > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("IPv4 sample step out of range: " + ipString + ", " + add);
        }
        return (int) address;
    }

    /**
     * 兼容旧转换入口，统一复用严格 IPv4 数值转换。
     *
     * @param ipv4Int 地址的 32 位表示
     * @return 点分 IPv4
     */
    public static String intToIpv4(int ipv4Int) {
        return IpConveter.longToIp(Integer.toUnsignedLong(ipv4Int));
    }
}

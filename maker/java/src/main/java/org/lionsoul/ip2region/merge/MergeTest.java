package org.lionsoul.ip2region.merge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.lionsoul.ip2region.maintenance.MaintenanceFiles;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 将采集的 IPv4 /24 归属补丁应用到新版七列数据，保留补丁范围外的归属与地区代码。
 * 补丁先通过有序区间索引处理，全部校验完成后才原子替换输出文件；不执行网络采集。实例数据不跨调用共享。
 */
public class MergeTest {

    /**
     * 打印命令用途；显式声明源格式，未指定输出时仍覆盖源文件。
     * @param args 命令行参数，仅为兼容旧入口保留
     */
    public static void printHelp(String[] args) {
        System.out.println("IPv4 merge: ipv4_source.txt merget.txt [output.txt] [--source-format=latest|legacy]");
    }

    /**
     * 加载有序源数据，应用补丁、合并相邻相同记录后写出新版七列文本。
     * 读取、解析和校验失败不修改输出；替换失败时抛出原始 I/O 异常。
     * @param args 源文件、补丁、可选输出，以及必须声明 --source-format=latest|legacy
     * @throws IOException 文件读取或替换失败
     */
    public static void main(String[] args) throws IOException {
        String sourceFormat = null;
        int positionalCount = args.length;
        if (args.length > 0 && args[args.length - 1].startsWith("--source-format=")) {
            sourceFormat = args[args.length - 1].substring("--source-format=".length());
            positionalCount--;
        }
        if (positionalCount < 2 || positionalCount > 3) {
            throw new IllegalArgumentException("IPv4 merge requires source, patch, optional output and --source-format=latest|legacy");
        }
        if (!"latest".equals(sourceFormat) && !"legacy".equals(sourceFormat)) {
            throw new IllegalArgumentException("explicit --source-format=latest|legacy is required");
        }
        List<RegionItem> regions = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (!line.trim().isEmpty()) {
                regions.add(new RegionItem(line, sourceFormat));
            }
        }
        validateIpv4Segments(regions);
        List<MergeRegionItem> patches = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8)) {
            if (!line.trim().isEmpty()) {
                patches.add(new MergeRegionItem(line));
            }
        }
        patches = patches.stream().distinct().sorted(Comparator.comparingLong(MergeRegionItem::getIp))
                .collect(Collectors.toList());
        // 按起始地址建有序索引；每个补丁只定位并拆分相交段，避免每次扫描整个 72 万段源库。
        NavigableMap<Long, RegionItem> indexedRegions = new TreeMap<>();
        for (RegionItem region : regions) {
            indexedRegions.put(region.getStart(), region);
        }
        for (MergeRegionItem patch : patches) {
            applyIpv4Patch(indexedRegions, patch);
        }
        regions = mergeAdjacentIpv4Segments(new ArrayList<>(indexedRegions.values()));
        validateIpv4Segments(regions);
        Path output = Paths.get(positionalCount == 3 ? args[2] : args[0]).toAbsolutePath();
        if (Files.exists(output) && Files.isSameFile(output, Paths.get(args[1]))) {
            throw new IllegalArgumentException("merge output must not overwrite patch input");
        }
        MaintenanceFiles.writeMaintenanceLines(output, regions.stream().map(MergeTest::formatIpv4Segment)
                .collect(Collectors.toList()));
    }

    /**
     * 校验段顺序、地址边界与连续性；允许输入是完整空间的连续子区间。
     * @param regions 按起始地址递增的非空记录
     * @throws IllegalArgumentException 空输入、越界、倒置、重叠或断档
     */
    private static void validateIpv4Segments(List<RegionItem> regions) {
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("empty IPv4 source");
        }
        long previous = regions.get(0).getStart() - 1;
        for (RegionItem region : regions) {
            if (region.getStart() != previous + 1 || region.getEnd() < region.getStart()
                    || region.getStart() < 0 || region.getEnd() > 0xFFFFFFFFL) {
                throw new IllegalArgumentException("invalid IPv4 segment: " + formatIpv4Segment(region));
            }
            previous = region.getEnd();
        }
    }

    /**
     * 用补丁覆盖整个 /24 与源段的交集；前后部分原样保留，不跨源段搬移归属。
     * @param regions 按起始地址索引的连续源段，原地更新
     * @param patch 仅包含国家、省、市的采集补丁
     * @throws IllegalArgumentException 补丁不在源范围内
     */
    private static void applyIpv4Patch(NavigableMap<Long, RegionItem> regions, MergeRegionItem patch) {
        Map.Entry<Long, RegionItem> sample = regions.floorEntry(patch.getIp());
        if (Objects.isNull(sample) || sample.getValue().getEnd() < patch.getIp()) {
            throw new IllegalArgumentException("IPv4 patch outside source: " + patch.getIp());
        }
        long[] range = IpConveter.ipStartAndEnd(patch.getIp());
        Map.Entry<Long, RegionItem> entry = regions.floorEntry(range[0]);
        if (Objects.isNull(entry)) {
            entry = regions.ceilingEntry(range[0]);
        }
        while (Objects.nonNull(entry) && entry.getKey() <= range[1]) {
            RegionItem region = entry.getValue();
            long start = Math.max(range[0], region.getStart());
            long end = Math.min(range[1], region.getEnd());
            if (start <= end) {
                regions.remove(region.getStart());
                if (region.getStart() < start) {
                    RegionItem head = copyIpv4Segment(region, region.getStart(), start - 1);
                    regions.put(head.getStart(), head);
                }
                RegionItem changed = copyIpv4Segment(region, start, end);
                changed.setCountry(Ipv4CountryNormalizer.normalizeIpv4Country(patch.getCountry()));
                changed.setCode(Ipv4CountryNormalizer.resolveIpv4CountryCode(changed.getCountry()));
                changed.setProvince(patch.getProvince());
                changed.setCity(patch.getCity());
                regions.put(changed.getStart(), changed);
                if (end < region.getEnd()) {
                    RegionItem tail = copyIpv4Segment(region, end + 1, region.getEnd());
                    regions.put(tail.getStart(), tail);
                }
            }
            // 跳过刚拆出的片段，继续下一个原始区间；防止重复应用同一补丁。
            entry = regions.higherEntry(region.getEnd());
        }
    }

    /**
     * 复制区间及全部地域字段，避免拆分时修改仍被引用的原段。
     * @param source 原记录
     * @param start 新起始地址，包含端点
     * @param end 新结束地址，包含端点
     * @return 独立记录
     */
    private static RegionItem copyIpv4Segment(RegionItem source, long start, long end) {
        RegionItem copy = new RegionItem(formatIpv4Segment(source));
        copy.setStart(start);
        copy.setEnd(end);
        return copy;
    }

    /**
     * 仅合并地址相邻且包括地区代码在内全部地域字段相同的记录。
     * @param regions 有序记录
     * @return 保持覆盖范围的合并结果
     */
    private static List<RegionItem> mergeAdjacentIpv4Segments(List<RegionItem> regions) {
        List<RegionItem> result = new ArrayList<>(regions.size());
        for (RegionItem region : regions) {
            RegionItem previous = result.isEmpty() ? null : result.get(result.size() - 1);
            if (Objects.nonNull(previous) && previous.getEnd() + 1 == region.getStart()
                    && region.equal(previous)) {
                previous.setEnd(region.getEnd());
            } else {
                result.add(region);
            }
        }
        return result;
    }

    /**
     * 输出新版固定顺序：起始 IP、结束 IP、国家、省、市、ISP、地区代码。
     * @param region 已解析的 IPv4 记录
     * @return 不含换行的七列文本
     */
    private static String formatIpv4Segment(RegionItem region) {
        return IpConveter.longToIp(region.getStart()) + "|" + IpConveter.longToIp(region.getEnd())
                + "|" + region.getCountry() + "|" + region.getProvince() + "|" + region.getCity()
                + "|" + region.getIsp() + "|" + region.getCode();
    }
}

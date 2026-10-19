package org.lionsoul.ip2region.merge.ipv6;

import org.lionsoul.ip2region.maintenance.MaintenanceFiles;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 独立 IPv6 区间补丁入口：先完整校验源与补丁，再拆分相交区间并原子输出独立文件。
 * 不修改源文件，不扩展覆盖空间，不采用隐含的 /64 等网段；实例状态仅限一次方法调用。
 */
public final class Ipv6MergeApp {
    /**
     * 通过静态入口执行，不保留跨任务状态。
     */
    private Ipv6MergeApp() {
    }

    /**
     * 读取七列源与同格式显式起止区间补丁，生成新版七列输出。
     *
     * @param args IPv6 源文件、补丁文件、独立输出文件
     * @throws IOException              文件读取或原子发布失败
     * @throws IllegalArgumentException 格式、区间或输出路径非法
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException("IPv6 merge: ipv6_source.txt ipv6_patch.txt output.txt");
        }
        mergeIpv6SourceFiles(Paths.get(args[0]), Paths.get(args[1]), Paths.get(args[2]));
    }

    /**
     * 源必须有序且无重叠，可包含空洞；补丁必须完整落在已有连续覆盖内。
     * 补丁按起点排序后检查互不重叠，不按输入顺序猜测冲突优先级。
     * 全部计算成功后输出，任何异常保留输入与已有输出内容。
     *
     * @param source      IPv6 七列源
     * @param patches     IPv6 七列补丁，可为空
     * @param destination 独立输出，不能指向任一输入，包括符号链接或硬链接
     * @throws IOException              文件操作失败
     * @throws IllegalArgumentException 重叠、空洞、越界或输入输出同文件
     */
    public static void mergeIpv6SourceFiles(Path source, Path patches, Path destination) throws IOException {
        Path input = source.toRealPath();
        Path patchInput = patches.toRealPath();
        Path output = destination.toAbsolutePath().normalize();
        if (output.equals(input) || output.equals(patchInput) || (Files.exists(output)
                && (Files.isSameFile(output, input) || Files.isSameFile(output, patchInput)))) {
            throw new IllegalArgumentException("IPv6 merge output must differ from both inputs");
        }
        List<Ipv6RegionSegment> sourceSegments = readIpv6SourceSegments(input, false);
        if (sourceSegments.isEmpty()) {
            throw new IllegalArgumentException("empty IPv6 source: " + source);
        }
        validateIpv6SegmentOrder(sourceSegments, "source");
        List<Ipv6RegionSegment> patchSegments = readIpv6SourceSegments(patchInput, true);
        patchSegments.sort(Comparator.comparing(Ipv6RegionSegment::getStartAddress));
        validateIpv6SegmentOrder(patchSegments, "patch");
        NavigableMap<BigInteger, Ipv6RegionSegment> indexed = new TreeMap<>();
        for (Ipv6RegionSegment segment : sourceSegments) {
            indexed.put(segment.getStartAddress(), segment);
        }
        for (Ipv6RegionSegment patch : patchSegments) {
            validateIpv6PatchCoverage(indexed, patch);
            applyIpv6SegmentPatch(indexed, patch);
        }
        List<Ipv6RegionSegment> merged = mergeAdjacentIpv6Segments(new ArrayList<>(indexed.values()));
        MaintenanceFiles.writeMaintenanceLines(output, merged.stream()
                .map(Ipv6RegionSegment::formatIpv6SourceSegment).collect(Collectors.toList()));
    }

    /**
     * 逐行读取，忽略空白行与整行 # 注释；错误附带文件路径及实际行号。
     *
     * @param input 输入路径
     * @param patch 是否解析为完整归属覆盖补丁
     * @return 按输入顺序读取的记录
     * @throws IOException              读取失败
     * @throws IllegalArgumentException 行内容非法，原异常作为 cause 保留
     */
    private static List<Ipv6RegionSegment> readIpv6SourceSegments(Path input, boolean patch) throws IOException {
        List<Ipv6RegionSegment> segments = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while (Objects.nonNull(line = reader.readLine())) {
                lineNumber++;
                if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                    continue;
                }
                try {
                    segments.add(patch ? Ipv6RegionSegment.parseIpv6PatchSegment(line)
                            : Ipv6RegionSegment.parseIpv6SourceSegment(line));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("invalid IPv6 data at " + input + ":" + lineNumber
                            + ", line=" + line + ", reason=" + e.getMessage(), e);
                }
            }
        }
        return segments;
    }

    /**
     * 检查顺序及不重叠，不要求填满整个 IPv6 地址空间。
     *
     * @param segments 有序记录
     * @param label    错误消息中的源或补丁阶段
     * @throws IllegalArgumentException 乱序、重叠或重复区间
     */
    private static void validateIpv6SegmentOrder(List<Ipv6RegionSegment> segments, String label) {
        Ipv6RegionSegment previous = null;
        for (Ipv6RegionSegment segment : segments) {
            if (Objects.nonNull(previous) && previous.getEndAddress().compareTo(segment.getStartAddress()) >= 0) {
                throw new IllegalArgumentException("overlapping or unordered IPv6 " + label + ": "
                        + previous.formatIpv6SourceSegment() + " / " + segment.formatIpv6SourceSegment());
            }
            previous = segment;
        }
    }

    /**
     * 在修改索引前验证补丁全范围连续存在，禁止跨源数据空洞或扩展边界。
     *
     * @param indexed 有序源区间
     * @param patch   完整归属补丁
     * @throws IllegalArgumentException 补丁覆盖到源中不存在的地址
     */
    private static void validateIpv6PatchCoverage(NavigableMap<BigInteger, Ipv6RegionSegment> indexed,
                                                  Ipv6RegionSegment patch) {
        BigInteger cursor = patch.getStartAddress();
        Map.Entry<BigInteger, Ipv6RegionSegment> entry = indexed.floorEntry(cursor);
        while (cursor.compareTo(patch.getEndAddress()) <= 0) {
            if (Objects.isNull(entry) || entry.getKey().compareTo(cursor) > 0
                    || entry.getValue().getEndAddress().compareTo(cursor) < 0) {
                throw new IllegalArgumentException("IPv6 patch outside source or across gap: "
                        + patch.formatIpv6SourceSegment() + ", missing=" + Ipv6AddressParser.formatIpv6Address(cursor));
            }
            cursor = entry.getValue().getEndAddress().add(BigInteger.ONE);
            entry = indexed.higherEntry(entry.getKey());
        }
    }

    /**
     * 仅拆分与补丁相交的源段；范围外复制原归属，范围内使用补丁的全部五列。
     *
     * @param indexed 已通过覆盖校验的源索引，原地更新
     * @param patch   互不重叠的显式区间补丁
     */
    private static void applyIpv6SegmentPatch(NavigableMap<BigInteger, Ipv6RegionSegment> indexed,
                                              Ipv6RegionSegment patch) {
        Map.Entry<BigInteger, Ipv6RegionSegment> entry = indexed.floorEntry(patch.getStartAddress());
        while (Objects.nonNull(entry) && entry.getKey().compareTo(patch.getEndAddress()) <= 0) {
            Ipv6RegionSegment source = entry.getValue();
            BigInteger start = source.getStartAddress().max(patch.getStartAddress());
            BigInteger end = source.getEndAddress().min(patch.getEndAddress());
            indexed.remove(source.getStartAddress());
            if (source.getStartAddress().compareTo(start) < 0) {
                Ipv6RegionSegment head = source.copyIpv6SegmentRange(source.getStartAddress(), start.subtract(BigInteger.ONE));
                indexed.put(head.getStartAddress(), head);
            }
            Ipv6RegionSegment changed = patch.copyIpv6SegmentRange(start, end);
            indexed.put(changed.getStartAddress(), changed);
            if (end.compareTo(source.getEndAddress()) < 0) {
                Ipv6RegionSegment tail = source.copyIpv6SegmentRange(end.add(BigInteger.ONE), source.getEndAddress());
                indexed.put(tail.getStartAddress(), tail);
            }
            entry = indexed.higherEntry(source.getEndAddress());
        }
    }

    /**
     * 合并相邻且全部五列一致的记录，不越过空洞；BigInteger 加一不会在最大地址回绕。
     *
     * @param segments 已排序、无重叠的结果
     * @return 等价覆盖的最简相邻区间列表
     */
    private static List<Ipv6RegionSegment> mergeAdjacentIpv6Segments(List<Ipv6RegionSegment> segments) {
        List<Ipv6RegionSegment> result = new ArrayList<>(segments.size());
        for (Ipv6RegionSegment segment : segments) {
            Ipv6RegionSegment previous = result.isEmpty() ? null : result.get(result.size() - 1);
            if (Objects.nonNull(previous) && previous.getEndAddress().add(BigInteger.ONE).equals(segment.getStartAddress())
                    && previous.getRegion().equals(segment.getRegion())) {
                result.set(result.size() - 1, previous.copyIpv6SegmentRange(previous.getStartAddress(), segment.getEndAddress()));
            } else {
                result.add(segment);
            }
        }
        return result;
    }
}

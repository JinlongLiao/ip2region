package org.lionsoul.ip2region.merge.ipv6;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.lionsoul.ip2region.MakerApp;
import org.lionsoul.ip2region.xdb.Util;

import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.ArrayList;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** 只在临时目录验证 IPv6 数值、显式区间补丁和制库，不改动真实 IPv4/IPv6 数据。 */
public class Ipv6MaintenanceTest {
    /** 每项测试使用隔离输入输出。 */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** 全零、最大值、大于 64 位、大小写及压缩形式均保持精确 128 位数值。 */
    @Test
    public void parseAndFormatIpv6Literals() {
        assertEquals(BigInteger.ZERO, Ipv6AddressParser.parseIpv6Address("::"));
        assertEquals(Ipv6AddressParser.MAX_IPV6_ADDRESS,
                Ipv6AddressParser.parseIpv6Address("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"));
        assertEquals("2001:db8::1", Ipv6AddressParser.formatIpv6Address(
                Ipv6AddressParser.parseIpv6Address("2001:0DB8:0000:0000:0000:0000:0000:0001")));
        assertEquals("1::2:0:0:3:4", Ipv6AddressParser.formatIpv6Address(
                Ipv6AddressParser.parseIpv6Address("1:0:0:2:0:0:3:4")));
        assertEquals("1:2:3:4:5:6:0:8", Ipv6AddressParser.formatIpv6Address(
                Ipv6AddressParser.parseIpv6Address("1:2:3:4:5:6:0:8")));
        assertEquals(BigInteger.ONE.shiftLeft(64), Ipv6AddressParser.parseIpv6Address("0:0:0:1::"));
    }

    /** 嵌入 IPv4 的合法 IPv6 不得因 JDK 解析策略折叠成四字节。 */
    @Test
    public void preserveIpv4EmbeddedIpv6As128Bits() throws Exception {
        BigInteger expected = new BigInteger("ffffc0000201", 16);
        assertEquals(expected, Ipv6AddressParser.parseIpv6Address("::ffff:192.0.2.1"));
        assertEquals(expected, Ipv6AddressParser.parseIpv6Address("::ffff:c000:201"));
        assertEquals(16, Util.parseIP("::ffff:192.0.2.1").length);
        assertEquals(4, Util.parseIP("192.0.2.1").length);
    }

    /** 与 JDK 的纯字节地址表示独立对照，校验任意 128 位值，不触发 DNS。 */
    @Test
    public void roundTripRandomIpv6AgainstIndependentByteRepresentation() throws Exception {
        Random random = new Random(4121L);
        for (int i = 0; i < 1000; i++) {
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            BigInteger expected = new BigInteger(1, bytes);
            assertEquals(expected, Ipv6AddressParser.parseIpv6Address(InetAddress.getByAddress(bytes).getHostAddress()));
            assertEquals(expected, Ipv6AddressParser.parseIpv6Address(Ipv6AddressParser.formatIpv6Address(expected)));
        }
    }

    /** 禁止 DNS、IPv4 误入、作用域、CIDR、端口括号、空组及越界。 */
    @Test
    public void rejectNonliteralAndMalformedIpv6Input() {
        for (String value : Arrays.asList("localhost", "1.2.3.4", "fe80::1%en0", "[::1]", "::/64",
                ":::", "1::2::3", "1:2:3", "1:2:3:4:5:6:7:8:9", "1:2:3:4:5:6:7:",
                "1:2:3:4:5:6:7:00000", "1:2:3:4:5:6:7::8", "::ffff:256.1.2.3", " ::1")) {
            assertThrows(value, IllegalArgumentException.class, () -> Ipv6AddressParser.parseIpv6Address(value));
        }
        assertThrows(IllegalArgumentException.class, () -> Ipv6AddressParser.formatIpv6Address(BigInteger.valueOf(-1)));
        assertThrows(IllegalArgumentException.class, () -> Ipv6AddressParser.formatIpv6Address(BigInteger.ONE.shiftLeft(128)));
    }

    /** 最小和最大单地址补丁保留中间全部空间，不出现负数或上溢回绕。 */
    @Test
    public void patchBothExtremeAddresses() throws Exception {
        Path source = createIpv6File("::|ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff|United States|A|B|OldISP|US");
        Path patch = createIpv6File("::|::|中国|甲|乙|NewISP|0",
                "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff|ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff|日本|丙|丁|OtherISP|0");
        Path output = temporaryFolder.newFile().toPath();
        Ipv6MergeApp.mergeIpv6SourceFiles(source, patch, output);
        List<String> rows = Files.readAllLines(output, StandardCharsets.UTF_8);
        assertEquals(3, rows.size());
        assertEquals("::|::|中国|甲|乙|NewISP|CN", rows.get(0));
        assertEquals("JP", rows.get(2).substring(rows.get(2).lastIndexOf('|') + 1));
        Ipv6RegionSegment middle = Ipv6RegionSegment.parseIpv6SourceSegment(rows.get(1));
        assertEquals(BigInteger.ONE, middle.getStartAddress());
        assertEquals(Ipv6AddressParser.MAX_IPV6_ADDRESS.subtract(BigInteger.ONE), middle.getEndAddress());
        assertEquals("United States|A|B|OldISP|US", middle.getRegion());
    }

    /** 跨 64 位边界和多个源段覆盖时，前后归属不变，补丁区使用完整五列。 */
    @Test
    public void patchAcrossSourceSegmentsAbove64Bits() throws Exception {
        Path source = createIpv6File("::|::ffff:ffff:ffff:ffff|United States|A|B|OldISP|US",
                "0:0:0:1::|0:0:0:1::ffff|Japan|Tokyo|Tokyo|OtherISP|JP");
        Path patch = createIpv6File("::ffff:ffff:ffff:fffe|0:0:0:1::1|香港|香港|葵涌|PatchISP|0");
        Path output = temporaryFolder.newFile().toPath();
        Ipv6MergeApp.mergeIpv6SourceFiles(source, patch, output);
        List<String> rows = Files.readAllLines(output, StandardCharsets.UTF_8);
        assertEquals(3, rows.size());
        assertEquals("中国|香港|葵涌|PatchISP|CN", Ipv6RegionSegment.parseIpv6SourceSegment(rows.get(1)).getRegion());
        assertTrue(rows.get(0).endsWith("United States|A|B|OldISP|US"));
        assertTrue(rows.get(2).endsWith("Japan|Tokyo|Tokyo|OtherISP|JP"));
    }

    /** 空补丁仍保留英文归属与原地区代码，只合并完全相同的相邻记录。 */
    @Test
    public void preserveSourceCodesAndOnlyMergeAdjacentEquivalentRegions() throws Exception {
        Path source = createIpv6File("2001:db8::|2001:db8::f|Japan|Tokyo|Tokyo|ISP|JP",
                "2001:db8::10|2001:db8::1f|Japan|Tokyo|Tokyo|ISP|JP",
                "2001:db8::20|2001:db8::2f|Japan|Tokyo|Tokyo|ISP|0",
                "2001:db8::40|2001:db8::4f|Japan|Tokyo|Tokyo|ISP|0");
        Path output = temporaryFolder.newFile().toPath();
        Ipv6MergeApp.mergeIpv6SourceFiles(source, createIpv6File(), output);
        List<String> rows = Files.readAllLines(output, StandardCharsets.UTF_8);
        assertEquals(3, rows.size());
        assertEquals("2001:db8::|2001:db8::1f|Japan|Tokyo|Tokyo|ISP|JP", rows.get(0));
    }

    /** 越界、空洞、重叠补丁或乱序源都拒绝，已有输出与输入保持原字节。 */
    @Test
    public void rejectInvalidCoverageWithoutPublishingOutput() throws Exception {
        Path source = createIpv6File("2001:db8::10|2001:db8::1f|Japan|A|B|ISP|JP",
                "2001:db8::30|2001:db8::3f|Japan|A|B|ISP|JP");
        byte[] original = Files.readAllBytes(source);
        Path output = createIpv6File("previous output");
        byte[] previousOutput = Files.readAllBytes(output);
        for (String row : Arrays.asList("2001:db8::|2001:db8::11|中国|甲|乙|ISP|CN",
                "2001:db8::1e|2001:db8::31|中国|甲|乙|ISP|CN",
                "2001:db8::3e|2001:db8::40|中国|甲|乙|ISP|CN")) {
            Path patch = createIpv6File(row);
            assertThrows(IllegalArgumentException.class, () -> Ipv6MergeApp.mergeIpv6SourceFiles(source, patch, output));
            assertArrayEquals(previousOutput, Files.readAllBytes(output));
            assertArrayEquals(original, Files.readAllBytes(source));
        }
        Path overlap = createIpv6File("2001:db8::10|2001:db8::15|中国|甲|乙|ISP|CN",
                "2001:db8::15|2001:db8::16|日本|甲|乙|ISP|JP");
        assertThrows(IllegalArgumentException.class, () -> Ipv6MergeApp.mergeIpv6SourceFiles(source, overlap, output));
        Path unordered = createIpv6File("2001:db8::30|2001:db8::3f|Japan|A|B|ISP|JP",
                "2001:db8::10|2001:db8::1f|Japan|A|B|ISP|JP");
        assertThrows(IllegalArgumentException.class, () -> Ipv6MergeApp.mergeIpv6SourceFiles(unordered, createIpv6File(), output));
        assertThrows(IllegalArgumentException.class, () -> Ipv6MergeApp.mergeIpv6SourceFiles(source, createIpv6File(), source));
        assertArrayEquals(previousOutput, Files.readAllBytes(output));
    }

    /** 地区代码冲突拒绝；独立入口不接受 IPv4 七列补丁。 */
    @Test
    public void rejectConflictingCodeAndIpv4Patch() {
        assertEquals("欧洲|0|0|ISP|0", Ipv6RegionSegment.parseIpv6PatchSegment(
                "2001:db8::|2001:db8::1|欧洲|0|0|ISP|CN").getRegion());
        assertThrows(IllegalArgumentException.class, () -> Ipv6RegionSegment.parseIpv6PatchSegment(
                "2001:db8::|2001:db8::1|中国|甲|乙|ISP|JP"));
        assertThrows(IllegalArgumentException.class, () -> Ipv6RegionSegment.parseIpv6SourceSegment(
                "1.0.0.0|1.0.0.255|中国|甲|乙|ISP|CN"));
        assertThrows(IllegalArgumentException.class, () -> Ipv6RegionSegment.parseIpv6SourceSegment(
                "2001:db8::2|2001:db8::1|中国|甲|乙|ISP|CN"));
    }

    /** 中英文大洲及跨国区域强制归零；未知名称显式代码保持；港澳台改写为 中国|地区 且代码为 CN。 */
    @Test
    public void normalizeAllExplicitRegionalPatchLabels() {
        for (String label : Arrays.asList("亚洲", "南美洲", "大洋洲", "南极洲", "Asia", "South America",
                "Oceania", "Antarctica", "asia", "Asia-Pacific", "全球")) {
            assertEquals(label + "|省|市|ISP|0", Ipv6RegionSegment.parseIpv6PatchSegment(
                    "::|::1|" + label + "|省|市|ISP|CN").getRegion());
        }
        assertEquals("Unlisted Country|省|市|ISP|JP",
                Ipv6RegionSegment.parseIpv6PatchSegment("::|::1|Unlisted Country|省|市|ISP|JP").getRegion());
        String[][] regions = {{"香港", "HK"}, {"澳门", "MO"}, {"台湾", "TW"}};
        for (String[] region : regions) {
            for (String code : Arrays.asList(region[1], "0", "CN")) {
                assertEquals("中国|" + region[0] + "|市|ISP|CN", Ipv6RegionSegment.parseIpv6PatchSegment(
                        "::|::1|" + region[0] + "|省|市|ISP|" + code).getRegion());
            }
        }
    }

    /** 随机的完整 128 位区间覆盖以独立端点检查，不能仅验证低 64 位。 */
    @Test
    public void mergeRandom128BitPatches() throws Exception {
        Random random = new Random(7391L);
        TreeSet<BigInteger> points = new TreeSet<>();
        while (points.size() < 100) {
            points.add(new BigInteger(128, random));
        }
        List<BigInteger> values = new ArrayList<>(points);
        List<String> patches = new ArrayList<>(50);
        for (int i = 0; i < values.size(); i += 2) {
            patches.add(Ipv6AddressParser.formatIpv6Address(values.get(i)) + "|"
                    + Ipv6AddressParser.formatIpv6Address(values.get(i + 1)) + "|中国|省" + i + "|市|ISP|CN");
        }
        Path source = createIpv6File("::|ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff|Japan|Tokyo|Tokyo|OldISP|JP");
        Path patch = temporaryFolder.newFile().toPath();
        Files.write(patch, patches, StandardCharsets.UTF_8);
        Path output = temporaryFolder.newFile().toPath();
        Ipv6MergeApp.mergeIpv6SourceFiles(source, patch, output);
        List<Ipv6RegionSegment> result = new ArrayList<>();
        for (String line : Files.readAllLines(output, StandardCharsets.UTF_8)) {
            result.add(Ipv6RegionSegment.parseIpv6SourceSegment(line));
        }
        BigInteger previous = BigInteger.valueOf(-1);
        for (Ipv6RegionSegment segment : result) {
            assertEquals(previous.add(BigInteger.ONE), segment.getStartAddress());
            previous = segment.getEndAddress();
        }
        assertEquals(Ipv6AddressParser.MAX_IPV6_ADDRESS, previous);
        for (int i = 0; i < values.size(); i++) {
            BigInteger point = values.get(i);
            String expected = "中国|省" + (i - i % 2) + "|市|ISP|CN";
            assertEquals(expected, findIpv6Region(result, point));
        }
    }

    /** 生成的合并文本可直接走新版 IPv6 制库流程，并独立核对每条二进制索引与归属。 */
    @Test
    public void generateIpv6XdbFromMergedSource() throws Exception {
        Path source = createIpv6File("2001:db8::|2001:db8::ffff|Japan|Tokyo|Tokyo|OldISP|JP");
        Path patch = createIpv6File("2001:db8::10|2001:db8::20|香港|香港|葵涌|NewISP|0");
        Path output = temporaryFolder.newFile().toPath();
        Path xdb = temporaryFolder.newFile().toPath();
        Ipv6MergeApp.mergeIpv6SourceFiles(source, patch, output);
        MakerApp.genDb(new String[]{"--src=" + output, "--dst=" + xdb, "--version=ipv6"});
        byte[] bytes = Files.readAllBytes(xdb);
        assertEquals(6, bytes[16]);
        int index = ByteBuffer.wrap(bytes, 8, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        List<String> expected = Files.readAllLines(output, StandardCharsets.UTF_8);
        for (String line : expected) {
            Ipv6RegionSegment segment = Ipv6RegionSegment.parseIpv6SourceSegment(line);
            assertEquals(segment.getStartAddress(), new BigInteger(1, Arrays.copyOfRange(bytes, index, index + 16)));
            assertEquals(segment.getEndAddress(), new BigInteger(1, Arrays.copyOfRange(bytes, index + 16, index + 32)));
            int length = Short.toUnsignedInt(ByteBuffer.wrap(bytes, index + 32, 2).order(ByteOrder.LITTLE_ENDIAN).getShort());
            int pointer = ByteBuffer.wrap(bytes, index + 34, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            assertEquals(segment.getRegion(), new String(bytes, pointer, length, StandardCharsets.UTF_8));
            index += 38;
        }
    }

    /**
     * 在隔离目录创建 UTF-8 输入。
     * @param lines 记录，可为空
     * @return 临时文件路径
     * @throws Exception 文件创建失败
     */
    private Path createIpv6File(String... lines) throws Exception {
        Path path = temporaryFolder.newFile().toPath();
        Files.write(path, Arrays.asList(lines), StandardCharsets.UTF_8);
        return path;
    }

    /**
     * 独立线性查找用于随机用例核对，不复用生产合并索引算法。
     * @param segments 合并后的无重叠区间
     * @param address 待查询地址
     * @return 命中的五列归属
     */
    private String findIpv6Region(List<Ipv6RegionSegment> segments, BigInteger address) {
        for (Ipv6RegionSegment segment : segments) {
            if (segment.getStartAddress().compareTo(address) <= 0 && segment.getEndAddress().compareTo(address) >= 0) {
                return segment.getRegion();
            }
        }
        throw new AssertionError("missing IPv6 address: " + address);
    }
}

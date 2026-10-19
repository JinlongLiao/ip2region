package org.lionsoul.ip2region.merge;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** 对隔离数据副本验证新版字段、区间拆分和清理行为，不调用网络采集器。 */
public class Ipv4MaintenanceTest {
    /** 每个测试独立创建文件，禁止触碰真实数据。 */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** 新格式读取时，ISP 和地区代码不能错位。 */
    @Test
    public void parseLatestFields() {
        RegionItem region = new RegionItem("1.0.0.0|1.0.0.255|中国|福建|福州|电信|CN");
        assertEquals("福建", region.getProvince());
        assertEquals("福州", region.getCity());
        assertEquals("电信", region.getIsp());
        assertEquals("CN", region.getCode());
    }

    /** 内部、头部、尾部与全覆盖补丁均应只改变目标 /24，输出保持连续。 */
    @Test
    public void splitPatchAtAllBoundaries() throws Exception {
        for (String address : Arrays.asList("1.0.0.9", "1.0.1.9", "1.0.2.9")) {
            Path source = temporaryFolder.newFile().toPath();
            Path patch = temporaryFolder.newFile().toPath();
            Path output = temporaryFolder.newFile().toPath();
            String original = "1.0.0.0|1.0.2.255|中国|旧省|旧市|电信|CN";
            Files.write(source, Collections.singletonList(original), StandardCharsets.UTF_8);
            Files.write(patch, Collections.singletonList(address + ",0,0,0,中国,新省,新市,采集ISP"), StandardCharsets.UTF_8);
            MergeTest.main(new String[]{source.toString(), patch.toString(), output.toString(), "--source-format=latest"});
            List<String> lines = Files.readAllLines(output, StandardCharsets.UTF_8);
            long previous = IpConveter.ipToLong("1.0.0.0") - 1;
            for (String line : lines) {
                RegionItem region = new RegionItem(line);
                assertEquals(previous + 1, region.getStart());
                assertEquals("电信", region.getIsp());
                assertEquals("CN", region.getCode());
                assertEquals(region.between(address) ? "新省" : "旧省", region.getProvince());
                previous = region.getEnd();
            }
            assertEquals(IpConveter.ipToLong("1.0.2.255"), previous);
            assertEquals(Collections.singletonList(original), Files.readAllLines(source, StandardCharsets.UTF_8));
        }
    }

    /** 一个补丁跨越多个源段时保留各段 ISP，国家改变时补齐新地区代码。 */
    @Test
    public void patchAcrossSegmentsAndRepairCountryCode() throws Exception {
        Path source = temporaryFolder.newFile().toPath();
        Path patch = temporaryFolder.newFile().toPath();
        Files.write(source, Arrays.asList("1.0.0.0|1.0.0.127|中国|甲|乙|电信|CN",
                "1.0.0.128|1.0.0.255|中国|甲|乙|联通|CN"), StandardCharsets.UTF_8);
        Files.write(patch, Collections.singletonList("1.0.0.9,0,0,0,日本,东京,东京"), StandardCharsets.UTF_8);
        MergeTest.main(new String[]{source.toString(), patch.toString(), "--source-format=latest"});
        assertEquals(Arrays.asList("1.0.0.0|1.0.0.127|日本|东京|东京|电信|JP",
                "1.0.0.128|1.0.0.255|日本|东京|东京|联通|JP"), Files.readAllLines(source, StandardCharsets.UTF_8));
    }

    /** 明确国家的旧占位代码先补齐，再合并相邻记录；坏输入不得覆盖源文件。 */
    @Test
    public void repairCountryCodesAndRejectInvalidSource() throws Exception {
        Path source = temporaryFolder.newFile().toPath();
        Path patch = temporaryFolder.newFile().toPath();
        List<String> original = Arrays.asList("1.0.0.0|1.0.0.127|中国|甲|乙|电信|CN",
                "1.0.0.128|1.0.0.255|中国|甲|乙|电信|0");
        Files.write(source, original, StandardCharsets.UTF_8);
        MergeTest.main(new String[]{source.toString(), patch.toString(), "--source-format=latest"});
        assertEquals(Collections.singletonList("1.0.0.0|1.0.0.255|中国|甲|乙|电信|CN"),
                Files.readAllLines(source, StandardCharsets.UTF_8));
        Files.write(source, Collections.singletonList("invalid"), StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> MergeTest.main(new String[]{source.toString(), patch.toString(), "--source-format=latest"}));
        assertEquals(Collections.singletonList("invalid"), Files.readAllLines(source, StandardCharsets.UTF_8));
    }

    /** 清理仅修改省市后缀，不改国家、ISP 或地区代码，且拒绝覆盖输入原件。 */
    @Test
    public void cleanOnlyProvinceAndCity() throws Exception {
        Path source = temporaryFolder.newFile().toPath();
        Path output = temporaryFolder.newFile().toPath();
        Files.write(source, Collections.singletonList("1.0.0.0|1.0.0.255|中国|福建省|福州市|测试市|CN"), StandardCharsets.UTF_8);
        ReplaceTest.main(new String[]{source.toString(), output.toString(), "--source-format=latest"});
        assertEquals(Collections.singletonList("1.0.0.0|1.0.0.255|中国|福建|福州|测试市|CN"),
                Files.readAllLines(output, StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,
                () -> ReplaceTest.main(new String[]{source.toString(), source.toString(), "--source-format=latest"}));
    }
    /** 非法地址不能进入清理区间算法，避免越界段覆盖有效地址。 */
    @Test
    public void rejectMalformedIpv4Address() {
        for (String address : Arrays.asList("1.2.3", "256.0.0.1", "1.2.-1.3", "1.2.3.4.", "")) {
            assertThrows(IllegalArgumentException.class, () -> IpConveter.ipToLong(address));
        }
        assertEquals(0xFFFFFFFFL, IpConveter.ipToLong("255.255.255.255"));
        assertEquals("255.255.255.255", IpConveter.longToIp(0xFFFFFFFFL));
    }
}

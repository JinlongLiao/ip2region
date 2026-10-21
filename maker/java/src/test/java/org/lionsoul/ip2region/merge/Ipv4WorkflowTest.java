package org.lionsoul.ip2region.merge;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.lionsoul.ip2region.MakerApp;
import org.lionsoul.ip2region.merge.ip.IpAreaNormalizer;
import org.lionsoul.ip2region.merge.ip.IpHelper;
import org.lionsoul.ip2region.merge.ip.pojo.AreaIsp;
import org.lionsoul.ip2region.xdb.IndexPolicy;
import org.lionsoul.ip2region.xdb.Maker;
import org.lionsoul.ip2region.xdb.Version;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * 在独立目录验证采集、旧格式迁移、合并、清理和制库，不请求真实供应方。
 */
public class Ipv4WorkflowTest {
    /**
     * 每条测试隔离文件，失败也不触碰真实数据。
     */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * 旧格式必须显式解释，不能将旧 ISP 当作新版地区代码。
     */
    @Test
    public void requireExplicitLegacyFormat() throws Exception {
        String legacy = "1.0.0.0|1.0.0.255|日本|0|东京|东京|电信";
        assertThrows(IllegalArgumentException.class, () -> new RegionItem(legacy));
        RegionItem parsed = new RegionItem(legacy, "legacy");
        assertEquals("东京", parsed.getProvince());
        assertEquals("电信", parsed.getIsp());
        assertEquals("JP", parsed.getCode());
        Path source = temporaryFolder.newFile().toPath();
        Path patch = temporaryFolder.newFile().toPath();
        Files.write(source, Collections.singletonList(legacy), StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> MergeTest.main(new String[]{source.toString(), patch.toString()}));
        assertEquals(Collections.singletonList(legacy), Files.readAllLines(source, StandardCharsets.UTF_8));
    }

    /**
     * 所有省份沿用实际采集行为；最终地址不会无符号回绕，结果写在外部文件。
     */
    @Test
    public void collectExternalFileWithoutAddressWrap() throws Exception {
        Path output = temporaryFolder.newFile().toPath();
        AtomicInteger calls = new AtomicInteger();
        IpLoopTest.collectIpv4PatchSamples("255.255.255.0", "255.255.255.255", output, ip -> {
            calls.incrementAndGet();
            return new AreaIsp("中国", "福建", "福州", "电信");
        });
        assertEquals(1, calls.get());
        assertEquals(Collections.singletonList("255.255.255.0,0,0,0,中国,福建,福州,电信"),
                Files.readAllLines(output, StandardCharsets.UTF_8));
    }

    /**
     * 中途采集失败不发布部分文件，也不能丢失上次输出。
     */
    @Test
    public void preserveCollectionOutputOnFailure() throws Exception {
        Path output = temporaryFolder.newFile().toPath();
        Files.write(output, Collections.singletonList("previous"), StandardCharsets.UTF_8);
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> IpLoopTest.collectIpv4PatchSamples(
                "1.0.0.0", "1.0.1.255", output, ip -> {
                    if (calls.incrementAndGet() == 2) {
                        throw new IllegalArgumentException("supplier failed");
                    }
                    return new AreaIsp("中国", "甲", "乙", "ISP");
                }));
        assertEquals(Collections.singletonList("previous"), Files.readAllLines(output, StandardCharsets.UTF_8));
    }

    /**
     * 从旧格式基础数据完成全链路，最后得到可发布的结构版本 3 IPv4 库。
     */
    @Test
    public void collectMergeCleanAndGenerateFromLegacySource() throws Exception {
        Path source = temporaryFolder.newFile().toPath();
        Path patch = temporaryFolder.newFile().toPath();
        Path merged = temporaryFolder.newFile().toPath();
        Path cleaned = temporaryFolder.newFile().toPath();
        Path binary = temporaryFolder.newFile().toPath();
        Files.write(source, Collections.singletonList("1.0.0.0|1.0.1.255|日本|0|东京|东京|原ISP"), StandardCharsets.UTF_8);
        IpLoopTest.collectIpv4PatchSamples("1.0.0.0", "1.0.1.255", patch,
                ip -> new AreaIsp("中国 ", "福建省", "福州市", "供应方ISP"));
        MergeTest.main(new String[]{source.toString(), patch.toString(), merged.toString(), "--source-format=legacy"});
        ReplaceTest.main(new String[]{merged.toString(), cleaned.toString(), "--source-format=latest"});
        assertEquals(Collections.singletonList("1.0.0.0|1.0.1.255|中国|福建|福州|供应方ISP|CN"),
                Files.readAllLines(cleaned, StandardCharsets.UTF_8));
        MakerApp.genDb(new String[]{"--src=" + cleaned, "--dst=" + binary, "--version=ipv4"});
        byte[] bytes = Files.readAllBytes(binary);
        assertTrue(bytes.length > 524544);
        assertEquals(3, bytes[0]);
        assertEquals(4, bytes[16]);
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("中国|福建|福州|供应方ISP|CN"));
        assertEquals(Collections.singletonList("1.0.0.0|1.0.1.255|日本|0|东京|东京|原ISP"),
                Files.readAllLines(source, StandardCharsets.UTF_8));
    }

    /**
     * 输入输出同文件以及坏源数据都不能破坏原始文本或已有正式库。
     */
    @Test
    public void protectSourceAndExistingBinaryOnMakerFailure() throws Exception {
        Path source = temporaryFolder.newFile().toPath();
        Path output = temporaryFolder.newFile().toPath();
        Files.write(source, Collections.singletonList("invalid source"), StandardCharsets.UTF_8);
        byte[] original = Files.readAllBytes(source);
        assertThrows(IllegalArgumentException.class, () -> MakerApp.genDb(new String[]{
                "--src=" + source, "--dst=" + source, "--version=ipv4"}));
        assertThrows(IllegalArgumentException.class, () -> new Maker(Version.IPv4, IndexPolicy.Vector,
                source.toString(), source.toString(), new int[0]));
        assertArrayEquals(original, Files.readAllBytes(source));
        Files.write(output, Arrays.asList("previous binary"), StandardCharsets.UTF_8);
        byte[] previousBinary = Files.readAllBytes(output);
        assertThrows(Exception.class, () -> MakerApp.genDb(new String[]{
                "--src=" + source, "--dst=" + output, "--version=ipv4"}));
        assertArrayEquals(previousBinary, Files.readAllBytes(output));
        assertArrayEquals(original, Files.readAllBytes(source));
        try (Stream<Path> files = Files.list(source.getParent())) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith("xdb-candidate-")));
        }
    }

    /**
     * 国家修复与 Python 采用同一份资源，区域和特殊标签不被强行映射。
     */
    @Test
    public void shareCountryRulesWithMigrationScripts() {
        assertEquals("AU", Ipv4CountryNormalizer.resolveIpv4CountryCode("澳大利亚"));
        assertEquals("JP", Ipv4CountryNormalizer.resolveIpv4CountryCode("日本"));
        assertEquals("TH", Ipv4CountryNormalizer.resolveIpv4CountryCode("泰国"));
        assertEquals("US", Ipv4CountryNormalizer.resolveIpv4CountryCode("美国"));
        assertEquals("XK", Ipv4CountryNormalizer.resolveIpv4CountryCode("科索沃"));
        assertEquals("印度尼西亚", Ipv4CountryNormalizer.normalizeIpv4Country("雅加达"));
        for (String region : Arrays.asList("欧洲", "亚太地区", "安的列斯", "阿森松岛", "圣马丁")) {
            assertEquals("0", Ipv4CountryNormalizer.resolveIpv4CountryCode(region));
        }
    }

    /**
     * 港澳台改写为 中国|香港、中国|澳门、中国|台湾 并解析为 CN，与存量数据一致。
     */
    @Test
    public void normalizeHongKongMacaoTaiwanAsChinaProvince() {
        for (String region : Arrays.asList("香港", "澳门", "台湾")) {
            AreaIsp normalized = IpAreaNormalizer.normalize(region, region, "原城市", "原ISP");
            assertEquals("中国", normalized.getCountry());
            assertEquals(region, normalized.getProvince());
            assertEquals("CN", Ipv4CountryNormalizer.resolveIpv4CountryCode(normalized.getCountry()));
        }
    }

    /**
     * 未完整写完索引前调用 end 必须失败；直接使用制库 API 也不能覆盖旧库。
     */
    @Test
    public void directMakerApiNeverPublishesPartialOutput() throws Exception {
        Path source = temporaryFolder.newFile().toPath();
        Path output = temporaryFolder.newFile().toPath();
        Files.write(source, Collections.singletonList("1.0.0.0|1.0.0.255|中国|甲|乙|ISP|CN"), StandardCharsets.UTF_8);
        Files.write(output, Collections.singletonList("existing binary"), StandardCharsets.UTF_8);
        byte[] previous = Files.readAllBytes(output);
        try (Maker maker = new Maker(Version.IPv4, IndexPolicy.Vector,
                source.toString(), output.toString(), new int[0])) {
            maker.init();
            assertArrayEquals(previous, Files.readAllBytes(output));
            assertThrows(IllegalStateException.class, maker::end);
        }
        assertArrayEquals(previous, Files.readAllBytes(output));
    }

    /**
     * 实际命令行失败必须非零退出，且错误输出带参数、原因和堆栈。
     */
    @Test
    public void makerCommandReportsFailureWithNonzeroExit() throws Exception {
        Path source = temporaryFolder.newFile().toPath();
        Path log = temporaryFolder.newFile().toPath();
        Files.write(source, Collections.singletonList("original source"), StandardCharsets.UTF_8);
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, MakerApp.class.getName(), "--src=" + source, "--dst=" + source, "--version=ipv4")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertTrue(process.waitFor(15, TimeUnit.SECONDS));
        assertNotEquals(0, process.exitValue());
        String error = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
        assertTrue(error.contains("source and destination must differ"));
        assertTrue(error.contains("args="));
        assertTrue(error.contains("at org.lionsoul.ip2region.MakerApp"));
        assertEquals(Collections.singletonList("original source"), Files.readAllLines(source, StandardCharsets.UTF_8));
    }

    /**
     * 无参数仍从原有源码范围开始，不要求 CLI；用首个样本抛错避免联网或覆盖文件。
     */
    @Test
    public void keepCodeConfiguredRangeWithoutArguments() throws Exception {
        IpHelper originalHelper = IpLoopTest.ipHelper;
        AtomicReference<String> firstSample = new AtomicReference<>();
        Path defaultOutput = Paths.get("merget.txt");
        byte[] originalOutput = Files.exists(defaultOutput) ? Files.readAllBytes(defaultOutput) : null;
        try {
            IpLoopTest.ipHelper = ip -> {
                firstSample.set(ip);
                throw new IllegalStateException("stop at first sample; no real provider request");
            };
            assertThrows(IllegalStateException.class, () -> IpLoopTest.main(new String[0]));
            assertEquals("36.6.27.0", firstSample.get());
            if (Objects.nonNull(originalOutput)) {
                assertArrayEquals(originalOutput, Files.readAllBytes(defaultOutput));
            } else {
                assertFalse(Files.exists(defaultOutput));
            }
        } finally {
            IpLoopTest.ipHelper = originalHelper;
        }
    }
}

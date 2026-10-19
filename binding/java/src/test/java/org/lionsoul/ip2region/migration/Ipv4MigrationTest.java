package org.lionsoul.ip2region.migration;

import org.junit.Test;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.ConfigTest;
import org.lionsoul.ip2region.service.Ip2Region;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Util;
import org.lionsoul.ip2region.xdb.Version;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;

/** 用真实迁移库验证所有源段的边界及代表点，同时保留旧库读取兼容性。 */
public class Ipv4MigrationTest {
    /**
     * 对每段首、尾、中间地址查询新版内存解析器，并按固定间隔对照文件和向量缓存解析。
     * @throws Exception 数据、解析或查询失败
     */
    @Test
    public void verifyEveryMigratedSegment() throws Exception {
        String path = ConfigTest.getDataPath("ip2region_v4.xdb");
        Searcher memory = Searcher.newWithBuffer(Version.IPv4, Searcher.loadContentFromFile(path));
        Searcher file = Searcher.newWithFileOnly(Version.IPv4, path);
        Searcher vector = Searcher.newWithVectorIndex(Version.IPv4, path, Searcher.loadVectorIndexFromFile(path));
        Searcher legacy = Searcher.newWithBuffer(Version.IPv4,
                Searcher.loadContentFromFile(ConfigTest.getDataPath("ip2region.xdb")));
        int count = 0;
        try (BufferedReader source = Files.newBufferedReader(Paths.get(ConfigTest.getDataPath("ipv4_source.txt")), StandardCharsets.UTF_8);
             BufferedReader original = Files.newBufferedReader(Paths.get(ConfigTest.getDataPath("ip.merge.txt")), StandardCharsets.UTF_8)) {
            String line;
            while ((line = source.readLine()) != null) {
                String[] fields = line.split("\\|", 3);
                String[] old = original.readLine().split("\\|", 3);
                long start = parseIpv4Number(fields[0]);
                long end = parseIpv4Number(fields[1]);
                for (long ip : new long[]{start, (start + end) / 2, end}) {
                    byte[] bytes = Util.parseIP(formatIpv4Address(ip));
                    assertEquals(fields[2], memory.search(bytes));
                    assertEquals(old[2], legacy.search(bytes));
                    if (count % 1000 == 0) {
                        assertEquals(fields[2], file.search(bytes));
                        assertEquals(fields[2], vector.search(bytes));
                    }
                }
                count++;
            }
            assertEquals(719901, count);
            assertEquals(null, original.readLine());
        } finally {
            memory.close();
            file.close();
            vector.close();
            legacy.close();
        }
    }

    /**
     * 每种缓存策略同时服务 IPv4/IPv6，所有工作线程的断言都通过 Future 回传主线程。
     * @throws Exception 查询、并发或关闭失败
     */
    @Test
    public void queryBothVersionsConcurrently() throws Exception {
        for (int policy : new int[]{Config.NoCache, Config.VIndexCache, Config.BufferCache}) {
            Config v4 = Config.custom().setCachePolicy(policy).setSearchers(2)
                    .setXdbPath(ConfigTest.getDataPath("ip2region_v4.xdb")).asV4();
            Config v6 = Config.custom().setCachePolicy(policy).setSearchers(2)
                    .setXdbPath(ConfigTest.getDataPath("ip2region_v6.xdb")).asV6();
            Ip2Region service = Ip2Region.create(v4, v6);
            ExecutorService executorService = Executors.newFixedThreadPool(4);
            try {
                List<Callable<Void>> tasks = new ArrayList<>(4);
                for (int worker = 0; worker < 4; worker++) {
                    tasks.add(() -> {
                        for (int i = 0; i < 1000; i++) {
                            assertEquals("中国|广东|深圳|电信|CN", service.search("113.92.157.29"));
                            assertEquals("中国|广东省|深圳市|电信|CN",
                                    service.search("240e:3b7:3272:d8d0:db09:c067:8d59:539e"));
                        }
                        return null;
                    });
                }
                for (Future<Void> result : executorService.invokeAll(tasks)) {
                    result.get();
                }
            } finally {
                executorService.shutdownNow();
                service.close();
            }
        }
    }

    /**
     * 将合法点分 IPv4 转成无符号数，用于选择区间代表点。
     * @param address 源文本中的合法 IPv4
     * @return 0 到 2^32-1 的地址数值
     */
    private static long parseIpv4Number(String address) {
        long result = 0;
        for (String part : address.split("\\.")) {
            result = (result << 8) | Integer.parseInt(part);
        }
        return result;
    }

    /**
     * 将代表点转回点分 IPv4，不进行主机名或网络解析。
     * @param address 无符号 IPv4 数值
     * @return 点分地址
     */
    private static String formatIpv4Address(long address) {
        return (address >>> 24) + "." + ((address >>> 16) & 255) + "."
                + ((address >>> 8) & 255) + "." + (address & 255);
    }
}

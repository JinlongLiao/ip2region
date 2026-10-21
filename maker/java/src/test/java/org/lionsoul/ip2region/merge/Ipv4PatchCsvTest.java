package org.lionsoul.ip2region.merge;

import org.junit.Test;
import org.lionsoul.ip2region.merge.ip.pojo.AreaIsp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** 验证含标点的供应方运营商按首项采集并合并，不破坏补丁和源库边界。 */
public class Ipv4PatchCsvTest {
    /** 供应方运营商只取首个逗号前的名称，采集及旧 CSV 合并均应用相同规则。 */
    @Test
    public void collectAndMergeQuotedOperator() throws Exception {
        Path directory = Files.createTempDirectory("ipv4-csv-test-");
        Path source = directory.resolve("source.txt");
        Path patch = directory.resolve("patch.csv");
        String operator = "NAMAVARAN e ASR e Novin Information Technology (FANHAB) , Private Joint-Stock Co \"ISP\"";
        String expected = "NAMAVARAN e ASR e Novin Information Technology (FANHAB)";
        try {
            Files.write(source, Collections.singletonList("185.192.112.0|185.192.112.255|伊朗|0|0|旧ISP|IR"), StandardCharsets.UTF_8);
            IpLoopTest.collectIpv4PatchSamples("185.192.112.0", "185.192.112.0", patch,
                    ip -> new AreaIsp("伊朗", "0", "0", operator));
            assertEquals(expected, new MergeRegionItem(Files.readAllLines(patch, StandardCharsets.UTF_8).get(0)).getIsp());
            Files.write(patch, Collections.singletonList(Ipv4PatchCsv.formatIpv4PatchCsv(
                    new String[]{"185.192.112.0", "0", "0", "0", "伊朗", "0", "0", operator})), StandardCharsets.UTF_8);
            MergeTest.main(new String[]{source.toString(), patch.toString(), "--source-format=latest"});
            assertEquals(Collections.singletonList("185.192.112.0|185.192.112.255|伊朗|0|0|" + expected + "|IR"),
                    Files.readAllLines(source, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(patch);
            Files.deleteIfExists(directory);
        }
    }

    /** 未闭合、错误引号、换行和竖线必须拒绝；历史尾部空运营商仍保留。 */
    @Test
    public void rejectMalformedPatchAndRetainEmptyOperator() {
        for (String operator : new String[]{"\"unfinished", "wrong\"quote", "\"ISP\"suffix", "bad|ISP", "bad\nISP"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new MergeRegionItem("1.0.0.1,0,0,0,中国,安徽,合肥," + operator));
        }
        assertEquals("", new MergeRegionItem("1.0.0.1,0,0,0,中国,安徽,合肥,").getIsp());
    }
}

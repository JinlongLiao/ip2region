package org.lionsoul.ip2region.merge.ip;

import com.wlzn.common.util.http.HttpResp;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 离线注入供应方失败，检查真实日志绑定、上下文及异常堆栈；不访问外网。 */
public class BaiduQifuLoggingTest {
    /** HTTP、业务和解析失败都可从日志定位，IO 异常保留原始栈。 */
    @Test
    public void recordProviderFailuresWithContextAndStack() throws Exception {
        assertTrue(LoggerFactory.getILoggerFactory().getClass().getName().contains("SimpleLoggerFactory"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream previous = System.err;
        try (PrintStream capture = new PrintStream(bytes, true, "UTF-8")) {
            System.setErr(capture);
            for (int scenario = 0; scenario < 4; scenario++) {
                final int selectedScenario = scenario;
                // 极小的请求替身仅属于本测试，保证真实供应方永远不会收到请求。
                BaiduQifuIpHelper helper = new BaiduQifuIpHelper() {
                    @Override
                    HttpResp requestBaiduIpResponse(String url) throws IOException {
                        if (selectedScenario == 0) {
                            throw new IOException("offline transport failure");
                        }
                        String body = selectedScenario == 1 ? "provider unavailable"
                                : selectedScenario == 2 ? "{\"code\":429}" : "invalid json";
                        return new HttpResp(selectedScenario == 1 ? 503 : 200,
                                body.getBytes(StandardCharsets.UTF_8), Collections.emptyMap(), 0);
                    }
                };
                assertNull(helper.queryIp("192.0.2.1"));
            }
        } finally {
            System.setErr(previous);
        }
        String output = bytes.toString("UTF-8");
        for (String expected : new String[]{"192.0.2.1", "method=GET", "https://qifu.baidu.com/",
                "status=503", "provider unavailable", "{\"code\":429}", "invalid json", "elapsedMs=",
                "java.io.IOException: offline transport failure", "BaiduQifuLoggingTest.java:", "stage=business"}) {
            assertTrue(expected + " missing from " + output, output.contains(expected));
        }
    }
}

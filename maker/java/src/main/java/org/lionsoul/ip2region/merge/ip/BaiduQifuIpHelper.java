package org.lionsoul.ip2region.merge.ip;

import com.alibaba.fastjson2.JSONObject;
import com.wlzn.common.util.http.HttpHelper;
import com.wlzn.common.util.http.HttpResp;
import org.lionsoul.ip2region.merge.ip.pojo.AreaIsp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** 百度企服 IP 采集器；失败记录完整调用上下文后返回空结果，由采集流程跳过该样本。 */
public class BaiduQifuIpHelper implements IpHelper {
    /** 由命令行产物中的 SLF4J 绑定输出失败信息与完整异常堆栈。 */
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * 查询单个 IP 的供应方归属，HTTP、业务或解析失败不生成补丁。
     * @param ip 待采集 IPv4 字面量
     * @return 规范化归属，供应方失败或无数据时返回 null
     */
    @Override
    public AreaIsp queryIp(String ip) {
        String url = "https://qifu.baidu.com/api/v1/ip-portrait/brief-info?ip=";
        long started = System.nanoTime();
        String body = null;
        Integer status = null;
        try {
            url += URLEncoder.encode(ip, "UTF-8");
            HttpResp httpResp = requestBaiduIpResponse(url);
            status = httpResp.getCode();
            body = httpResp.getBodyString();
            if (!httpResp.isOk()) {
                log.error("baidu qifu stage=http ip={} method=GET url={} status={} response={} elapsedMs={}",
                        ip, url, status, body, (System.nanoTime() - started) / 1000000);
                return null;
            }
            JSONObject jsonObject = JSONObject.parseObject(body);
            if (Objects.isNull(jsonObject) || jsonObject.getIntValue("code") != 200) {
                log.warn("baidu qifu stage=business ip={} method=GET url={} status={} response={} elapsedMs={}",
                        ip, url, status, body, (System.nanoTime() - started) / 1000000);
                return null;
            }
            JSONObject data = jsonObject.getJSONObject("data");
            if (Objects.isNull(data)) {
                log.warn("baidu qifu stage=data ip={} method=GET url={} status={} response={} elapsedMs={}",
                        ip, url, status, body, (System.nanoTime() - started) / 1000000);
                return null;
            }
            return buildArea(data);
        } catch (IOException | RuntimeException e) {
            log.error("baidu qifu stage=request-or-parse ip={} method=GET url={} status={} response={} elapsedMs={} type={} message={}",
                    ip, url, status, body, (System.nanoTime() - started) / 1000000,
                    e.getClass().getName(), e.getMessage(), e);
        }
        return null;
    }

    /**
     * 发起供应方 HTTP 请求；包内可替换以离线验证失败边界。
     * @param url 含已编码 IP 参数的完整地址
     * @return 供应方原始响应
     * @throws IOException 传输失败
     */
    HttpResp requestBaiduIpResponse(String url) throws IOException {
        return HttpHelper.get(url, headers(), null);
    }

    /** @return 供应方所需请求头；每次新建以避免共享可变状态。 */
    private Map<String, Object> headers() {
        Map<String, Object> headers = new HashMap<>(4);
        headers.put("Accept", "*/*");
        headers.put("Referer", "https://www.baidu.com/");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:152.0) Gecko/20100101 Firefox/152.0");
        return headers;
    }

    /**
     * 将供应方结构化数据转换为项目归属，缺失省市回退至上级区域。
     * @param data 供应方 data 对象
     * @return 规范化归属
     */
    AreaIsp buildArea(JSONObject data) {
        String country = data.getString("country");
        String province = defaultIfBlank(data.getString("province"), country);
        String city = defaultIfBlank(data.getString("city"), province);
        return IpAreaNormalizer.normalize(country, province, city, data.getString("isp"));
    }

    /**
     * 空字段回退至已知上级归属。
     * @param value 原字段
     * @param defaultValue 回退归属
     * @return 非空原字段或回退值
     */
    private String defaultIfBlank(String value, String defaultValue) {
        if (Objects.isNull(value) || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value;
    }
}

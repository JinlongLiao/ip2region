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

public class BaiduQifuIpHelper implements IpHelper {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public AreaIsp queryIp(String ip) {
        try {
            String url = "https://qifu.baidu.com/api/v1/ip-portrait/brief-info?ip="
                    + URLEncoder.encode(ip, "UTF-8");
            HttpResp httpResp = HttpHelper.get(url, headers(), null);
            if (!httpResp.isOk()) {
                log.error("bad baidu qifu resp:{}", httpResp);
                return null;
            }
            JSONObject jsonObject = JSONObject.parseObject(httpResp.getBodyString());
            if (jsonObject.getIntValue("code") != 200) {
                log.warn("bad baidu qifu body:{}", httpResp.getBodyString());
                return null;
            }
            JSONObject data = jsonObject.getJSONObject("data");
            if (data == null) {
                log.warn("empty baidu qifu data:{}", httpResp.getBodyString());
                return null;
            }
            return buildArea(data);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private Map<String, Object> headers() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("Accept", "*/*");
        headers.put("Referer", "https://www.baidu.com/");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:152.0) Gecko/20100101 Firefox/152.0");
        return headers;
    }

    AreaIsp buildArea(JSONObject data) {
        String country = data.getString("country");
        String province = defaultIfBlank(data.getString("province"), country);
        String city = defaultIfBlank(data.getString("city"), province);
        return IpAreaNormalizer.normalize(country, province, city, data.getString("isp"));
    }

    private String defaultIfBlank(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value;
    }
}

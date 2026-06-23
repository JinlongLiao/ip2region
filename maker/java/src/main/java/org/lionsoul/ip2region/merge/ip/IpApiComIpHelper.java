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

public class IpApiComIpHelper implements IpHelper {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public AreaIsp queryIp(String ip) {
        try {
            String url = "http://ip-api.com/json/" + URLEncoder.encode(ip, "UTF-8")
                    + "?lang=zh-CN&fields=status,message,country,regionName,city,isp,query";
            HttpResp httpResp = HttpHelper.get(url, null, null);
            if (!httpResp.isOk()) {
                log.error("bad ip-api.com resp:{}", httpResp);
                return null;
            }
            JSONObject jsonObject = JSONObject.parseObject(httpResp.getBodyString());
            if (!"success".equals(jsonObject.getString("status"))) {
                log.warn("bad ip-api.com body:{}", httpResp.getBodyString());
                return null;
            }
            return buildArea(jsonObject);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    AreaIsp buildArea(JSONObject data) {
        String country = data.getString("country");
        String province = data.getString("regionName");
        String city = data.getString("city");
        if ("香港".equals(country)) {
            return IpAreaNormalizer.normalize(country, "香港", defaultIfBlank(province, city), data.getString("isp"));
        }
        return IpAreaNormalizer.normalize(country, province, city, data.getString("isp"));
    }

    private String defaultIfBlank(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value;
    }

}

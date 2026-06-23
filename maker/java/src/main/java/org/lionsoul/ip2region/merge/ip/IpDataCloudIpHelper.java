package org.lionsoul.ip2region.merge.ip;


import com.wlzn.common.parent.web.common.json.JsonHelper;
import com.wlzn.common.util.http.HttpHelper;
import com.wlzn.common.util.http.HttpResp;
import org.lionsoul.ip2region.merge.ip.pojo.AreaIsp;
import org.lionsoul.ip2region.merge.ip.pojo.Data;
import org.lionsoul.ip2region.merge.ip.pojo.IpDataCloudResp;
import org.lionsoul.ip2region.merge.ip.pojo.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Objects;

/**
 * @author: liaojinlong
 * @date: 2024-11-07 14:58
 */
public class IpDataCloudIpHelper implements IpHelper {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final JsonHelper jsonHelper;

    public IpDataCloudIpHelper() {
        this.jsonHelper = new JsonHelper();
    }

    /**
     * 搜索IP信息
     *
     * @param ip
     * @return
     */
    public AreaIsp queryIp(String ip) {
        return _queryIp(ip);
    }

    /**
     * <pre>
     *      {
     *     "code": 200,
     *     "data": {
     *         "location": {
     *             "area_code": "",
     *             "city": "Bangkok",
     *             "city_code": "",
     *             "continent": "亚洲",
     *             "country": "泰国",
     *             "country_code": "TH",
     *             "district": "",
     *             "elevation": "",
     *             "ip": "38.54.32.198",
     *             "isp": "靠谱云",
     *             "latitude": "13.753980",
     *             "longitude": "100.501440",
     *             "province": "Bangkok",
     *             "street": "",
     *             "time_zone": "Asia/Bangkok",
     *             "weather_station": "",
     *             "zip_code": "10200"
     *         }
     *     },
     *     "msg": "success"
     * }
     *  </pre>
     */

    protected AreaIsp _queryIp(String ip) {

        String url = "https://api.ipdatacloud.com/v2/query?key=676d6d03b87411f09e5e00163e167ffb&ip=" + ip;
        try {
            HttpResp httpResp = HttpHelper.get(url, null, null);
            if (httpResp.isOk()) {
                IpDataCloudResp ipDataCloudResp = this.jsonHelper.fromJson(httpResp.getBodyString(), IpDataCloudResp.class);
                if (Objects.isNull(ipDataCloudResp) || ipDataCloudResp.getCode() != 200) {
                    log.warn("bad resp:{}", httpResp);
                    return null;
                }
                Data data = ipDataCloudResp.getData();
                Location location = data.getLocation();
                String city = location.getCity();
                String country = location.getCountry();
                String province = location.getProvince();
                return new AreaIsp(country, province, city, location.getIsp());
            } else {
                log.error("bad resp:{}", httpResp);
                return null;
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
}

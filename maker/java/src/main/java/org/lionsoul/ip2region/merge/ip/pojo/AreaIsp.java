package org.lionsoul.ip2region.merge.ip.pojo;

import com.wlzn.common.parent.web.common.ip.Area;

/**
 * @author: liaojinlong
 * @date: 2024-11-14 13:58
 */
public class AreaIsp extends Area {
    private final String isp;

    public AreaIsp(String country, String province, String city, String isp) {
        super(country, province, city);
        this.isp = isp;
    }

    public String getIsp() {
        return isp;
    }

    @Override
    public String toString() {
        return "AreaIsp(super=" + super.toString() + ", isp=" + this.isp + ")";
    }
}

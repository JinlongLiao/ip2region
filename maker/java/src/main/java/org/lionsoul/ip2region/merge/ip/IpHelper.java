package org.lionsoul.ip2region.merge.ip;

import org.lionsoul.ip2region.merge.ip.pojo.AreaIsp;

public interface IpHelper {
    AreaIsp queryIp(String ip);
}

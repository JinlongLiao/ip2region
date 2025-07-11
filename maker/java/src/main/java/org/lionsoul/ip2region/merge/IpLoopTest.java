package org.lionsoul.ip2region.merge;

import org.lionsoul.ip2region.merge.ip.IpDataCloudIpHelper;
import org.lionsoul.ip2region.merge.ip.pojo.AreaIsp;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Objects;

public class IpLoopTest {
    static IpDataCloudIpHelper ipDataCloudIpHelper = new IpDataCloudIpHelper();

    public static void main(String[] args) throws IOException {
        StringBuilder buffer = new StringBuilder();
        int startIp = ip2Int("8.211.208.0", 0);
        int endIp = ip2Int("8.219.1.255", 0);
        while (isLegalIp(startIp, endIp)) {
            String ip = intToIpv4(startIp);
            AreaIsp areaIsp = ipDataCloudIpHelper.queryIp(ip);
            if (Objects.isNull(areaIsp)) {
                continue;
            }
            buffer.append(ip);
            buffer.append(",0,0,0,");
            buffer.append(areaIsp.getCountry());
            buffer.append(",");
            buffer.append(areaIsp.getProvince());
            buffer.append(",");
            buffer.append(areaIsp.getCity());
            buffer.append(",");
            buffer.append(areaIsp.getIsp());
            buffer.append("\n");
            startIp = ip2Int(ip, 1);
            System.out.println("ip = " + ip);
        }
        URL resource = IpLoopTest.class.getResource("/merget.txt");
        File file = new File(resource.getFile());
        file.createNewFile();
        Files.write(file.toPath(), buffer.toString().getBytes());
    }

    static boolean isLegalIp(int startIp, int endIp) {
        long s = Integer.toUnsignedLong(startIp);
        long e = Integer.toUnsignedLong(endIp);
        return e >= s;
    }

    /**
     * 将 ip 字符串转换为 int 类型的数字
     * <p>
     * 思路就是将 ip 的每一段数字转为 8 位二进制数，并将它们放在结果的适当位置上
     *
     * @param ipString ip字符串，如 127.0.0.1
     * @return ip字符串对应的 int 值
     */
    public static int ip2Int(String ipString, int add) {
        // 取 ip 的各段
        String[] ipSlices = ipString.split("\\.");
        int ip1 = Integer.parseInt(ipSlices[0]);
        int ip2 = Integer.parseInt(ipSlices[1]);
        int ip3 = Integer.parseInt(ipSlices[2]);
        int ip4 = Integer.parseInt(ipSlices[3]);
        if (add > 0) {
            ip3 += add;
            if (ip3 > 255) {
                ip3 -= 255;
                ip2 += 1;
                if (ip2 > 255) {
                    ip2 -= 255;
                    ip1 += 1;
                }
            }

        }
        int[] ips = new int[]{ip1, ip2, ip3, ip4};
        int rs = 0;
        for (int i = 0; i < ips.length; i++) {
            // 将 ip 的每一段解析为 int，并根据位置左移 8 位
            int intSlice = ips[i] << 8 * (3 - i);
            // 或运算
            rs = rs | intSlice;
        }
        return rs;
    }

    /**
     * 将 int类型的数字转换为IP地址（IPV4）字符串
     *
     * @param ipv4Int 用 int表示的IP地址（IPV4）字符串
     * @return IP地址（IPV4）字符串，如 127.0.0.1
     */
    public static String intToIpv4(int ipv4Int) {
        String[] ipString = new String[4];
        for (int i = 0; i < 4; i++) {
            // 每 8 位为一段，这里取当前要处理的最高位的位置
            int pos = i * 8;

            // 取当前处理的 ip 段的值
            int and = ipv4Int & (255 << pos);

            // 将当前 ip 段转换为 0 ~ 255 的数字，注意这里必须使用无符号右移
            ipString[3 - i] = String.valueOf(and >>> pos);
        }

        return String.join(".", ipString);
    }

}

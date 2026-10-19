package org.lionsoul.ip2region.merge;

import java.util.Objects;

/** 自维护 IPv4 区间工具；仅处理点分十进制地址，不解析主机名或 IPv6。 */
public class IpConveter {

    /**
     * 校验四个十进制八位组后转换为无符号地址，防止清理时将非法输入转换成其他地址。
     * 此协议校验不使用通用字符串转换，避免接受负数、空段或越界段。
     * @param ip 点分十进制 IPv4
     * @return 0 到 2^32-1 的地址数值
     * @throws IllegalArgumentException 地址缺失、非四段或八位组非法
     */
    public static long ipToLong(String ip) {
        if (Objects.isNull(ip)) {
            throw new IllegalArgumentException("IPv4 address is null");
        }
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("invalid IPv4 address: " + ip);
        }
        long result = 0;
        for (String part : parts) {
            if (!part.matches("[0-9]{1,3}") || Integer.parseInt(part) > 255) {
                throw new IllegalArgumentException("invalid IPv4 address: " + ip);
            }
            result = (result << 8) | Integer.parseInt(part);
        }
        return result;
    }

    /**
     * 转换合法无符号 IPv4 数值；不截断越界值。
     * @param num 0 到 2^32-1 的地址数值
     * @return 点分十进制地址
     * @throws IllegalArgumentException 数值越界
     */
    public static String longToIp(long num) {
        if (num < 0 || num > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("IPv4 number out of range: " + num);
        }
        return (num >>> 24) + "." + ((num >>> 16) & 0xff) + "."
                + ((num >>> 8) & 0xff) + "." + (num & 0xff);
    }

    /**
     * 计算样本所属 /24 闭区间，不扩展为其他前缀。
     * @param ip 合法无符号 IPv4 数值
     * @return 起始及结束地址，均包含端点
     * @throws IllegalArgumentException 地址数值越界
     */
    public static long[] ipStartAndEnd(long ip) {
        if (ip < 0 || ip > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("IPv4 number out of range: " + ip);
        }
        return new long[]{ip & ~0xffL, ip | 0xffL};
    }
}

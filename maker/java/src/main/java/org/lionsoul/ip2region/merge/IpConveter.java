package org.lionsoul.ip2region.merge;

/**
 * @author: liaojinlong
 * @date: 2024-07-30 09:21
 */
public class IpConveter {

    public static long ipToLong(String ip) {
        String[] split = ip.split("\\.");
        long num = 0L;
        num = num | Long.parseLong(split[0]) << 24;
        num = num | Long.parseLong(split[1]) << 16;
        num = num | Long.parseLong(split[2]) << 8;
        num = num | Long.parseLong(split[3]);
        return num;
    }

    public static String longToIp(long num) {
        return (num >> 24 & 0xff) +
                "." +
                (num >> 16 & 0xff) +
                "." +
                (num >> 8 & 0xff) +
                "." +
                (num & 0xff);
    }

    public static long[] ipStartAndEnd(long ip) {
        long[] longs = new long[2];
        longs[0] = (ip & ~0xff);
        longs[1] = ip | 0xff;
        return longs;
    }
}

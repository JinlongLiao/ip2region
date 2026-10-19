package org.lionsoul.ip2region.merge.ipv6;

import org.lionsoul.ip2region.merge.IpConveter;

import java.math.BigInteger;
import java.util.Objects;

/**
 * 纯数值 IPv6 解析器；以无符号 128 位值计算区间，不执行 DNS，不接受作用域、端口或 CIDR。
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4291#section-2.2">RFC 4291 section 2.2</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc5952#section-4">RFC 5952 section 4</a>
 */
public final class Ipv6AddressParser {
    /** 合法 IPv6 最大值，含 128 个 1；不可变并可跨线程共享。 */
    public static final BigInteger MAX_IPV6_ADDRESS = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);

    /** 仅提供无状态转换，不实例化解析器。 */
    private Ipv6AddressParser() {
    }

    /**
     * 接受八组十六进制、一次双冒号压缩以及末尾 IPv4 嵌入形式，保持完整 128 位。
     * @param address IPv6 字面量，无首尾空白、方括号、作用域或前缀长度
     * @return 0 到 2^128-1 的无符号地址
     * @throws IllegalArgumentException 地址为空、非 IPv6 或分组非法
     */
    public static BigInteger parseIpv6Address(String address) {
        if (Objects.isNull(address) || !address.contains(":")) {
            throw new IllegalArgumentException("invalid IPv6 literal: " + address);
        }
        String value = address;
        if (value.contains(".")) {
            int delimiter = value.lastIndexOf(':');
            long embedded = IpConveter.ipToLong(value.substring(delimiter + 1));
            value = value.substring(0, delimiter + 1) + Long.toHexString(embedded >>> 16)
                    + ":" + Long.toHexString(embedded & 0xFFFF);
        }
        String[] compressed = value.split("::", -1);
        if (compressed.length > 2) {
            throw new IllegalArgumentException("multiple IPv6 compression markers: " + address);
        }
        int[] left = parseIpv6Groups(compressed[0]);
        int[] right = compressed.length == 2 ? parseIpv6Groups(compressed[1]) : new int[0];
        int zeros = 8 - left.length - right.length;
        if ((compressed.length == 1 && zeros != 0) || (compressed.length == 2 && zeros < 1)) {
            throw new IllegalArgumentException("invalid IPv6 group count: " + address);
        }
        BigInteger result = BigInteger.ZERO;
        for (int group : left) {
            result = result.shiftLeft(16).or(BigInteger.valueOf(group));
        }
        result = result.shiftLeft(zeros * 16);
        for (int group : right) {
            result = result.shiftLeft(16).or(BigInteger.valueOf(group));
        }
        return result;
    }

    /**
     * 解析单侧的十六进制分组，空侧仅供双冒号压缩使用。
     * @param side 冒号分隔的分组
     * @return 各组无符号 16 位数值
     * @throws IllegalArgumentException 空组、非十六进制或超过四位
     */
    private static int[] parseIpv6Groups(String side) {
        if (side.isEmpty()) {
            return new int[0];
        }
        String[] groups = side.split(":", -1);
        int[] values = new int[groups.length];
        for (int i = 0; i < groups.length; i++) {
            if (!groups[i].matches("[0-9a-fA-F]{1,4}")) {
                throw new IllegalArgumentException("invalid IPv6 group: " + groups[i]);
            }
            values[i] = Integer.parseInt(groups[i], 16);
        }
        return values;
    }

    /**
     * 输出小写十六进制、移除组内前导零，并压缩最左侧最长的至少两组零。
     * IPv4 嵌入地址也输出完整 IPv6 十六进制形式，不降为四字节 IPv4。
     * @param address 合法无符号 IPv6 数值
     * @return 规范压缩的 IPv6 文本
     * @throws IllegalArgumentException 数值为空、负数或超出 128 位
     */
    public static String formatIpv6Address(BigInteger address) {
        if (Objects.isNull(address) || address.signum() < 0 || address.compareTo(MAX_IPV6_ADDRESS) > 0) {
            throw new IllegalArgumentException("IPv6 value out of range: " + address);
        }
        int[] groups = new int[8];
        int bestStart = -1;
        int bestLength = 1;
        for (int i = 0; i < 8; i++) {
            groups[i] = address.shiftRight((7 - i) * 16).intValue() & 0xFFFF;
        }
        for (int i = 0; i < 8;) {
            if (groups[i] != 0) {
                i++;
                continue;
            }
            int end = i;
            while (end < 8 && groups[end] == 0) {
                end++;
            }
            if (end - i > bestLength) {
                bestStart = i;
                bestLength = end - i;
            }
            i = end;
        }
        StringBuilder output = new StringBuilder(39);
        for (int i = 0; i < 8;) {
            if (i == bestStart) {
                output.append("::");
                i += bestLength;
            } else {
                if (output.length() > 0 && output.charAt(output.length() - 1) != ':') {
                    output.append(':');
                }
                output.append(Integer.toHexString(groups[i++]));
            }
        }
        return output.toString();
    }

    /**
     * 转为制库器使用的十六字节网络序，避免 Java 将 IPv4 映射地址折叠成四字节。
     * @param address 合法 IPv6 字面量
     * @return 固定十六字节的大端地址
     */
    public static byte[] parseIpv6Bytes(String address) {
        byte[] encoded = parseIpv6Address(address).toByteArray();
        byte[] bytes = new byte[16];
        int length = Math.min(16, encoded.length);
        System.arraycopy(encoded, encoded.length - length, bytes, 16 - length, length);
        return bytes;
    }
}

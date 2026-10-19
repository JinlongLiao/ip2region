package org.lionsoul.ip2region.merge.ipv6;

import com.wlzn.common.util.common.StringUtil;
import org.lionsoul.ip2region.merge.Ipv4CountryNormalizer;

import java.math.BigInteger;
import java.util.Objects;

/** 不可变 IPv6 闭区间及完整五列归属；源记录保留既有英文名称和地区代码。 */
public final class Ipv6RegionSegment {
    /** 包含的起始地址，合法范围 0 到 2^128-1。 */
    private final BigInteger startAddress;
    /** 包含的结束地址，不小于起始地址。 */
    private final BigInteger endAddress;
    /** 国家或地区、省、市、ISP、地区代码；不可变字符串，保持五列顺序。 */
    private final String region;

    /**
     * 构建已校验的不可变区间，内部拆分仅修改地址，不改写归属。
     * @param startAddress 起始地址
     * @param endAddress 结束地址
     * @param region 完整五列归属
     */
    private Ipv6RegionSegment(BigInteger startAddress, BigInteger endAddress, String region) {
        if (Objects.isNull(startAddress) || Objects.isNull(endAddress) || startAddress.signum() < 0
                || endAddress.compareTo(startAddress) < 0
                || endAddress.compareTo(Ipv6AddressParser.MAX_IPV6_ADDRESS) > 0) {
            throw new IllegalArgumentException("invalid IPv6 segment bounds");
        }
        this.startAddress = startAddress;
        this.endAddress = endAddress;
        this.region = region;
    }

    /**
     * 读取新版七列 IPv6 源记录，保留所有归属列，禁止将 IPv4 或 CIDR 作为端点。
     * @param line 起始、结束、国家或地区、省、市、ISP、地区代码
     * @return 不可变 IPv6 记录
     * @throws IllegalArgumentException 列数、归属、代码或端点非法
     */
    public static Ipv6RegionSegment parseIpv6SourceSegment(String line) {
        String[] fields = line.split("\\|", -1);
        if (fields.length != 7 || StringUtil.isBlank(fields[2]) || !fields[6].matches("0|[A-Z]{2}")) {
            throw new IllegalArgumentException("IPv6 source requires seven valid fields: " + line);
        }
        for (String field : fields) {
            if (field.contains("\n") || field.contains("\r")) {
                throw new IllegalArgumentException("IPv6 record must be a single line");
            }
        }
        BigInteger start = Ipv6AddressParser.parseIpv6Address(fields[0]);
        BigInteger end = Ipv6AddressParser.parseIpv6Address(fields[1]);
        String region = String.join("|", fields[2], fields[3], fields[4], fields[5], fields[6]);
        return new Ipv6RegionSegment(start, end, region);
    }

    /**
     * 解析完整归属补丁；明确中文国家地区按项目规则补齐代码，英文名称保留显式代码；大洲及已确认歧义标签统一为 0。
     * 港澳台与 IPv4 采集器一致改写为 中国|香港、中国|澳门、中国|台湾，代码为 CN。
     * 非零显式代码与已知名称矛盾时拒绝，避免静默发布冲突数据。
     * @param line 与源相同的七列补丁，ISP 等全部归属列均作为覆盖值
     * @return 已校验并规范明确名称的补丁
     * @throws IllegalArgumentException 补丁非法或代码与明确名称矛盾
     */
    public static Ipv6RegionSegment parseIpv6PatchSegment(String line) {
        Ipv6RegionSegment parsed = parseIpv6SourceSegment(line);
        String[] fields = parsed.region.split("\\|", -1);
        fields[0] = Ipv4CountryNormalizer.normalizeIpv4Country(fields[0]);
        if (Ipv4CountryNormalizer.isChinaRegionLabel(fields[0])) {
            // 补丁原有 HK/MO/TW 指同一地区，不视为与 CN 冲突。
            if (fields[4].equals(Ipv4CountryNormalizer.resolveIpv4CountryCode(fields[0]))) {
                fields[4] = "0";
            }
            fields[1] = fields[0];
            fields[0] = "中国";
        }
        String expected = Ipv4CountryNormalizer.resolveIpv4CountryCode(fields[0]);
        if (Ipv4CountryNormalizer.requiresZeroRegionCode(fields[0])) {
            fields[4] = "0";
        } else if (!"0".equals(expected)) {
            if (!"0".equals(fields[4]) && !expected.equals(fields[4])) {
                throw new IllegalArgumentException("IPv6 patch region code conflicts with name: " + line);
            }
            fields[4] = expected;
        }
        return new Ipv6RegionSegment(parsed.startAddress, parsed.endAddress, String.join("|", fields));
    }

    /** @return 闭区间起始地址，不可变。 */
    public BigInteger getStartAddress() {
        return startAddress;
    }

    /** @return 闭区间结束地址，不可变。 */
    public BigInteger getEndAddress() {
        return endAddress;
    }

    /** @return 不含地址的完整五列归属。 */
    public String getRegion() {
        return region;
    }

    /**
     * 复制归属到新的合法闭区间，原记录不变。
     * @param start 新起始地址
     * @param end 新结束地址
     * @return 与原归属一致的新记录
     */
    public Ipv6RegionSegment copyIpv6SegmentRange(BigInteger start, BigInteger end) {
        return new Ipv6RegionSegment(start, end, region);
    }

    /** @return 可直接交给新版制库器的七列记录，不带换行。 */
    public String formatIpv6SourceSegment() {
        return Ipv6AddressParser.formatIpv6Address(startAddress) + "|"
                + Ipv6AddressParser.formatIpv6Address(endAddress) + "|" + region;
    }
}

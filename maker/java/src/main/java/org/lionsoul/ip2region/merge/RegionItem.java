package org.lionsoul.ip2region.merge;

import java.util.Objects;

import static org.lionsoul.ip2region.merge.IpConveter.ipToLong;

/** 新版 IPv4 七列记录；code 表示末列地区代码，区间按无符号 IPv4 数值保存。 */
public class RegionItem implements Comparable<RegionItem> {
    /** 用于历史去重的记录标识，默认空字符串；仅在当前维护流程使用，不保证并发访问安全。 */
    private String id = "";
    /** 包含端点的无符号 IPv4 起始数值；仅在当前维护流程使用，不保证并发访问安全。 */
    private long start;
    /** 包含端点的无符号 IPv4 结束数值；仅在当前维护流程使用，不保证并发访问安全。 */
    private long end;
    /** 国家名称，保持维护数据原值；仅在当前维护流程使用，不保证并发访问安全。 */
    private String country;
    /** 新版末列地区代码，未知时为 0；仅在当前维护流程使用，不保证并发访问安全。 */
    private String code = "0";
    /** 省或地区名称；仅在当前维护流程使用，不保证并发访问安全。 */
    private String province;
    /** 城市名称；仅在当前维护流程使用，不保证并发访问安全。 */
    private String city;
    /** ISP 名称，补丁拆分时保留源段值；仅在当前维护流程使用，不保证并发访问安全。 */
    private String isp = "0";

    /**
     * 创建待填充的 IPv4 记录，未知地区代码与 ISP 默认值为 0。
     */
    public RegionItem() {
    }

    /**
     * 创建新版七列 IPv4 记录；有参形式按国家、省、市、ISP、地区代码读取。
     * @param line 新版七列源数据
     */
    public RegionItem(String line) {
        this(line, "latest");
    }

    /**
     * 依据显式格式读取七列；禁止把旧 code 位置当成新版省字段。
     * @param line 七列 IPv4 源记录
     * @param sourceFormat latest 或 legacy，旧数据必须明确指定 legacy
     * @throws IllegalArgumentException 格式、地址、区间或代码列非法
     */
    public RegionItem(String line, String sourceFormat) {
        String[] fields = line.split("\\|", -1);
        if (fields.length != 7 || (!"latest".equals(sourceFormat) && !"legacy".equals(sourceFormat))) {
            throw new IllegalArgumentException("IPv4 source requires seven fields and latest/legacy format: " + line);
        }
        boolean legacy = "legacy".equals(sourceFormat);
        String inputCode = fields[legacy ? 3 : 6];
        if (!inputCode.matches("0|[A-Z]{2}")) {
            throw new IllegalArgumentException("invalid country code; check --source-format=legacy: " + line);
        }
        this.start = ipToLong(fields[0]);
        this.end = ipToLong(fields[1]);
        if (start > end) {
            throw new IllegalArgumentException("reversed IPv4 segment: " + line);
        }
        this.country = Ipv4CountryNormalizer.normalizeIpv4Country(fields[2]);
        this.province = fields[legacy ? 4 : 3];
        this.city = fields[legacy ? 5 : 4];
        this.isp = fields[legacy ? 6 : 5];
        this.code = Ipv4CountryNormalizer.resolveIpv4CountryCode(country);
    }

    /**
     * 读取包含端点的无符号 IPv4 起始数值。
     * @return 对应字段值或比较结果
     */
    public long getStart() {
        return start;
    }

    /**
     * 更新包含端点的无符号 IPv4 起始数值。
     * @param start 对应字段的新值
     */
    public void setStart(long start) {
        this.start = start;
    }

    /**
     * 读取包含端点的无符号 IPv4 结束数值。
     * @return 对应字段值或比较结果
     */
    public long getEnd() {
        return end;
    }

    /**
     * 更新包含端点的无符号 IPv4 结束数值。
     * @param end 对应字段的新值
     */
    public void setEnd(long end) {
        this.end = end;
    }

    /**
     * 读取国家名称，保持维护数据原值。
     * @return 对应字段值或比较结果
     */
    public String getCountry() {
        return country;
    }

    /**
     * 更新国家名称，保持维护数据原值。
     * @param country 对应字段的新值
     */
    public void setCountry(String country) {
        this.country = country;
    }

    /**
     * 读取新版末列地区代码，未知时为 0。
     * @return 对应字段值或比较结果
     */
    public String getCode() {
        return code;
    }

    /**
     * 更新新版末列地区代码，未知时为 0。
     * @param code 对应字段的新值
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * 读取省或地区名称。
     * @return 对应字段值或比较结果
     */
    public String getProvince() {
        return province;
    }

    /**
     * 更新省或地区名称。
     * @param province 对应字段的新值
     */
    public void setProvince(String province) {
        this.province = province;
    }

    /**
     * 读取城市名称。
     * @return 对应字段值或比较结果
     */
    public String getCity() {
        return city;
    }

    /**
     * 更新城市名称。
     * @param city 对应字段的新值
     */
    public void setCity(String city) {
        this.city = city;
    }

    /**
     * 读取ISP 名称，补丁拆分时保留源段值。
     * @return 对应字段值或比较结果
     */
    public String getIsp() {
        return isp;
    }

    /**
     * 更新ISP 名称，补丁拆分时保留源段值。
     * @param isp 对应字段的新值
     */
    public void setIsp(String isp) {
        this.isp = isp;
    }

    /**
     * 判断地址是否落在当前闭区间。
     * @param ip 待判断的 IPv4 地址
     * @return 地址位于闭区间时为 true
     */
    public boolean between(String ip) {
        long l = ipToLong(ip);
        return between(l);
    }

    /**
     * 判断地址是否落在当前闭区间。
     * @param ip 待判断的 IPv4 地址
     * @return 地址位于闭区间时为 true
     */
    public boolean between(long ip) {
        return ip >= start && ip <= end;
    }

    /**
     * 读取用于历史去重的记录标识，默认空字符串。
     * @return 对应字段值或比较结果
     */
    public String getId() {
        return id;
    }

    /**
     * 按历史约定对起始地址倒序比较。
     * @param o 参与比较的记录
     * @return 对应字段值或比较结果
     */
    @Override
    public int compareTo(RegionItem o) {
        return Long.compare(o.getStart(), getStart());
    }

    /**
     * 比较全部地域字段及地区代码，供相邻段合并使用。
     * @param regionItem 参与比较的地域记录
     * @return 对应字段值或比较结果
     */
    public boolean equal(RegionItem regionItem) {
        if (Objects.isNull(regionItem)) {
            return false;
        }
        return Objects.equals(getCountry(), regionItem.getCountry())
                && Objects.equals(getProvince(), regionItem.getProvince())
                && Objects.equals(getCity(), regionItem.getCity())
                && Objects.equals(getCode(), regionItem.getCode())
                && Objects.equals(getIsp(), regionItem.getIsp())
                ;
    }

    /**
     * 按当前毫秒时间更新历史去重标识。
     */
    public void changeId() {
        this.id = String.valueOf(System.currentTimeMillis());
    }

    /**
     * 比较记录内容及历史标识。
     * @param o 参与比较的记录
     * @return 对应字段值或比较结果
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RegionItem)) {
            return false;
        }
        RegionItem that = (RegionItem) o;
        return getStart() == that.getStart()
                && getEnd() == that.getEnd()
                && Objects.equals(getCountry(), that.getCountry())
                && Objects.equals(getId(), that.getId())
                && Objects.equals(getCode(), that.getCode())
                && Objects.equals(getProvince(), that.getProvince())
                && Objects.equals(getCity(), that.getCity()) && Objects.equals(getIsp(), that.getIsp());
    }

    /**
     * 根据 equals 所使用字段生成散列值。
     * @return 对应字段值或比较结果
     */
    @Override
    public int hashCode() {
        return Objects.hash(getStart(), getId(), getEnd(), getCountry(), getCode(), getProvince(), getCity(), getIsp());
    }
}

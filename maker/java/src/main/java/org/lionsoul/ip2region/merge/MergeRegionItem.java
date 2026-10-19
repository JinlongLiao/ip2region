package org.lionsoul.ip2region.merge;

import java.util.Objects;

import static org.lionsoul.ip2region.merge.IpConveter.ipToLong;

/** 采集补丁模型，仅承载 IPv4 样本及国家、省、市，不直接解释新版源数据列。 */
public class MergeRegionItem implements Comparable<MergeRegionItem> {
    /** 补丁样本的无符号 IPv4 地址；仅在当前维护流程使用，不保证并发访问安全。 */
    private long ip;
    /** 国家名称，保持维护数据原值；仅在当前维护流程使用，不保证并发访问安全。 */
    private String country;
    /** 省或地区名称；仅在当前维护流程使用，不保证并发访问安全。 */
    private String province;
    /** 城市名称；仅在当前维护流程使用，不保证并发访问安全。 */
    private String city;

    /**
     * 创建待填充的采集补丁。
     */
    public MergeRegionItem() {
    }

    /**
     * 读取历史七列或当前采集器八列 CSV；ISP 保留在采集文件但补丁只更改国家、省、市。
     * @param line IP、三个占位字段、国家、省、市，以及可选 ISP
     * @throws IllegalArgumentException 列数或必需归属字段不合法
     */
    public MergeRegionItem(String line) {
        String[] split = line.split(",", -1);
        if ((split.length != 7 && split.length != 8) || split[4].isEmpty()
                || split[5].isEmpty() || split[6].isEmpty()) {
            throw new IllegalArgumentException("invalid IPv4 patch: " + line);
        }
        this.ip = ipToLong(split[0]);
        this.country = split[4];
        this.province = split[5];
        this.city = split[6];
    }

    /**
     * 读取补丁样本的无符号 IPv4 地址。
     * @return 对应字段值或比较结果
     */
    public long getIp() {
        return ip;
    }

    /**
     * 更新补丁样本的无符号 IPv4 地址。
     * @param ip 待判断的 IPv4 地址
     */
    public void setIp(long ip) {
        this.ip = ip;
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
     * 按历史约定对起始地址倒序比较。
     * @param o 参与比较的记录
     * @return 对应字段值或比较结果
     */
    @Override
    public int compareTo(MergeRegionItem o) {
        return Long.compare(o.getIp(), getIp());
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
        if (!(o instanceof MergeRegionItem)) {
            return false;
        }
        MergeRegionItem that = (MergeRegionItem) o;
        return getIp() == that.getIp() && Objects.equals(getCountry(), that.getCountry()) && Objects.equals(getProvince(), that.getProvince()) && Objects.equals(getCity(), that.getCity());
    }

    /**
     * 根据 equals 所使用字段生成散列值。
     * @return 对应字段值或比较结果
     */
    @Override
    public int hashCode() {
        return Objects.hash(getIp(), getCountry(), getProvince(), getCity());
    }
}

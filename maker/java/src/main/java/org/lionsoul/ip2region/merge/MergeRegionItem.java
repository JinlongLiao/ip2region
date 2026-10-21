package org.lionsoul.ip2region.merge;

import static org.lionsoul.ip2region.merge.IpConveter.ipToLong;

import java.util.Objects;

/**
 * 采集补丁模型，承载 IPv4 样本及国家、省、市和可选运营商，不直接解释新版源数据列。
 */
public class MergeRegionItem implements Comparable<MergeRegionItem> {
    /**
     * 补丁样本的无符号 IPv4 地址；仅在当前维护流程使用，不保证并发访问安全。
     */
    private long ip;
    /**
     * 国家名称，保持维护数据原值；仅在当前维护流程使用，不保证并发访问安全。
     */
    private String country;
    /**
     * 省或地区名称；仅在当前维护流程使用，不保证并发访问安全。
     */
    private String province;
    /**
     * 城市名称；仅在当前维护流程使用，不保证并发访问安全。
     */
    private String city;

    /**
     * 可选运营商；空串表示未提供有效值，合并时保留源 ISP。
     */
    private String isp = "";

    /**
     * 创建待填充的采集补丁。
     */
    public MergeRegionItem() {
    }

    /**
     * 读取历史七列或当前采集器八列 CSV；有效 ISP 参与覆盖，只取第一个逗号前的名称并去掉开头的“中国”；缺列、空白或 0 保留源 ISP。
     *
     * @param line IP、三个占位字段、国家、省、市，以及可选 ISP
     * @throws IllegalArgumentException 列数或必需归属字段不合法
     */
    public MergeRegionItem(String line) {
        String[] split = Ipv4PatchCsv.parseIpv4PatchCsv(line);
        if ((split.length != 7 && split.length != 8) || split[4].isEmpty()
                || split[5].isEmpty() || split[6].isEmpty()) {
            throw new IllegalArgumentException("invalid IPv4 patch: " + line);
        }
        this.ip = ipToLong(split[0]);
        this.country = split[4];
        this.province = split[5];
        this.city = split[6];
        if (split.length == 8) {
            this.isp = normalizeIpv4Operator(split[7]);
        }
    }

    /**
     * 统一采集及合并的运营商名称，只保留第一个英文逗号前的名称并去掉“中国”前缀。
     *
     * @param operator 非空原始运营商字段
     * @return 去除两侧空格后的名称；空白或 0 返回空串，合并时保留原值
     */
    static String normalizeIpv4Operator(String operator) {
        int comma = operator.indexOf(',');
        String normalized = (comma < 0 ? operator : operator.substring(0, comma)).trim();
        if (normalized.startsWith("中国")) {
            normalized = normalized.substring(2).trim();
        }
        return "0".equals(normalized) ? "" : normalized;
    }

    /**
     * 读取补丁样本的无符号 IPv4 地址。
     *
     * @return 对应字段值或比较结果
     */
    public long getIp() {
        return ip;
    }

    /**
     * 更新补丁样本的无符号 IPv4 地址。
     *
     * @param ip 待判断的 IPv4 地址
     */
    public void setIp(long ip) {
        this.ip = ip;
    }

    /**
     * 读取国家名称，保持维护数据原值。
     *
     * @return 对应字段值或比较结果
     */
    public String getCountry() {
        return country;
    }

    /**
     * 更新国家名称，保持维护数据原值。
     *
     * @param country 对应字段的新值
     */
    public void setCountry(String country) {
        this.country = country;
    }

    /**
     * 读取省或地区名称。
     *
     * @return 对应字段值或比较结果
     */
    public String getProvince() {
        return province;
    }

    /**
     * 更新省或地区名称。
     *
     * @param province 对应字段的新值
     */
    public void setProvince(String province) {
        this.province = province;
    }

    /**
     * 读取城市名称。
     *
     * @return 对应字段值或比较结果
     */
    public String getCity() {
        return city;
    }

    /**
     * 更新城市名称。
     *
     * @param city 对应字段的新值
     */
    public void setCity(String city) {
        this.city = city;
    }

    /**
     * 读取规范化后的补丁运营商。
     *
     * @return 不含开头“中国”的名称；空串表示保留源 ISP
     */
    public String getIsp() {
        return isp;
    }

    /**
     * 按历史约定对起始地址倒序比较。
     *
     * @param o 参与比较的记录
     * @return 对应字段值或比较结果
     */
    @Override
    public int compareTo(MergeRegionItem o) {
        return Long.compare(o.getIp(), getIp());
    }

    /**
     * 比较记录内容及历史标识。
     *
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
        return getIp() == that.getIp() && Objects.equals(getCountry(), that.getCountry()) && Objects.equals(getProvince(), that.getProvince()) && Objects.equals(getCity(), that.getCity()) && Objects.equals(getIsp(), that.getIsp());
    }

    /**
     * 根据 equals 所使用字段生成散列值。
     *
     * @return 对应字段值或比较结果
     */
    @Override
    public int hashCode() {
        return Objects.hash(getIp(), getCountry(), getProvince(), getCity(), getIsp());
    }
}

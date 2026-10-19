package org.lionsoul.ip2region.merge.ip;

import com.wlzn.common.util.common.StringUtil;

import org.lionsoul.ip2region.merge.Ipv4CountryNormalizer;
import org.lionsoul.ip2region.merge.ip.pojo.AreaIsp;

/**
 * 规范供应方归属名称；香港、澳门、台湾改写为 中国|香港、中国|澳门、中国|台湾，与存量数据一致。
 */
public class IpAreaNormalizer {

    /**
     * 沿用省市简称与缺省回填规则；国家为香港、澳门或台湾时改为中国，原标签移入省列，与存量 IPv4 数据一致。
     *
     * @param country  供应方国家或地区标签
     * @param province 省或地区，可为空并回填国家标签
     * @param city     城市，可为空并回填省或地区
     * @param isp      ISP 原值
     * @return 无共享可变状态的归属记录
     */
    public static AreaIsp normalize(String country, String province, String city, String isp) {
        country = Ipv4CountryNormalizer.normalizeIpv4Country(normalizeCountry(country));
        province = normalizeProvince(defaultIfBlank(province, country));
        city = normalizeCity(defaultIfBlank(city, province));

        if (Ipv4CountryNormalizer.isChinaRegionLabel(country)) {
            province = country;
            country = "中国";
        }

        return new AreaIsp(country, province, city, isp);
    }

    /**
     * 保留历史俄罗斯名称别名处理，不变更其他国家或地区。
     *
     * @param country 供应方名称
     * @return 历史兼容名称
     */
    private static String normalizeCountry(String country) {
        if ("俄罗斯联邦".equals(country)) {
            return "俄罗斯";
        }
        return country;
    }

    /**
     * 沿用自治区特别行政区简称与省后缀清理。
     *
     * @param province 已回填的非空地区名
     * @return 兼容现有维护数据的省名
     */
    private static String normalizeProvince(String province) {
        if ("香港特别行政区".equals(province)) {
            return "香港";
        }
        if ("澳门特别行政区".equals(province)) {
            return "澳门";
        }
        if (province.endsWith("省")) {
            return province.substring(0, province.length() - 1);
        }
        return province;
    }

    /**
     * 沿用城市末尾行政单位清理，不修改中间字符。
     *
     * @param city 已回填的非空城市名
     * @return 去掉末尾市字的城市名
     */
    private static String normalizeCity(String city) {
        return city.endsWith("市") ? city.substring(0, city.length() - 1) : city;
    }

    /**
     * 使用公共字符串工具的空白约定，空字符串与全空白均回填。
     *
     * @param value        供应方字段
     * @param defaultValue 业务缺省字段
     * @return 原字段或缺省字段
     */
    private static String defaultIfBlank(String value, String defaultValue) {
        return StringUtil.isBlank(value) ? defaultValue : value;
    }
}

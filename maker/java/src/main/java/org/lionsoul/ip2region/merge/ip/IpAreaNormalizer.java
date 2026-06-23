package org.lionsoul.ip2region.merge.ip;

import org.lionsoul.ip2region.merge.ip.pojo.AreaIsp;

public class IpAreaNormalizer {

    public static AreaIsp normalize(String country, String province, String city, String isp) {
        country = normalizeCountry(country);
        province = normalizeProvince(defaultIfBlank(province, country));
        city = normalizeCity(defaultIfBlank(city, province));

        if ("香港".equals(country)) {
            country = "中国";
            province = "香港";
        } else if ("台湾".equals(country)) {
            country = "中国";
            province = "台湾";
        }

        return new AreaIsp(country, province, city, isp);
    }

    private static String normalizeCountry(String country) {
        if ("俄罗斯联邦".equals(country)) {
            return "俄罗斯";
        }
        return country;
    }

    private static String normalizeProvince(String province) {
        if ("香港特别行政区".equals(province)) {
            return "香港";
        }
        if ("澳门特别行政区".equals(province)) {
            return "澳门";
        }
        if (province != null && province.endsWith("省")) {
            return province.substring(0, province.length() - 1);
        }
        return province;
    }

    private static String normalizeCity(String city) {
        if (city != null && city.endsWith("市")) {
            return city.substring(0, city.length() - 1);
        }
        return city;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value;
    }
}

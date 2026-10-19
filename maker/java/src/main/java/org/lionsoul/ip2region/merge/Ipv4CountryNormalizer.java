package org.lionsoul.ip2region.merge;

import com.alibaba.fastjson2.JSONObject;
import com.wlzn.common.util.common.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;

/**
 * 项目级 IPv4 国家修复规则，与迁移脚本共享同一份名称、别名和代码资源。
 * 只修正明确误填及空白；大洲、跨国区域和歧义标签代码为 0。
 * @see <a href="https://github.com/unicode-org/cldr/blob/release-48/common/main/zh.xml">CLDR 48 中文名称</a>
 */
public final class Ipv4CountryNormalizer {
    /** 只读地区名称到代码映射，类初始化后可安全并发读取。 */
    private static final Map<String, String> COUNTRY_CODES;
    /** 经人工确认的国家误填修正，不按供应方当前 IP 归属替换旧数据。 */
    private static final Map<String, String> COUNTRY_CORRECTIONS;

    /** 大洲、跨国区域及歧义标签，初始化后只读；中英文大小写统一比较。 */
    private static final Set<String> ZERO_CODE_LABELS;

    /** 国家列需改写为 中国|地区 的港澳台标签，初始化后只读。 */
    private static final Set<String> CHINA_REGION_LABELS;

    static {
        try (InputStream input = Ipv4CountryNormalizer.class.getResourceAsStream(
                "/ipv4-country-codes/国家名称代码映射.json")) {
            if (Objects.isNull(input)) {
                throw new IllegalStateException("missing project IPv4 country code resource");
            }
            JSONObject data = JSONObject.parseObject(StreamUtils.copyToString(input, StandardCharsets.UTF_8));
            Set<String> labels = new HashSet<>();
            for (String label : data.getJSONArray("强制零地区代码标签").toJavaList(String.class)) {
                labels.add(label.toLowerCase(Locale.ROOT));
            }
            ZERO_CODE_LABELS = Collections.unmodifiableSet(labels);
            CHINA_REGION_LABELS = Collections.unmodifiableSet(
                    new HashSet<>(data.getJSONArray("归入中国地区标签").toJavaList(String.class)));
            COUNTRY_CODES = loadCountryMapping(data.getJSONObject("名称代码"));
            COUNTRY_CORRECTIONS = loadCountryMapping(data.getJSONObject("已确认国家字段修正"));
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** 工具只提供无状态静态操作，禁止实例化。 */
    private Ipv4CountryNormalizer() {
    }

    /**
     * 从资源构建只读映射；缺失配置立即失败，不静默清空代码。
     * @param data 结构完整的 JSON 名称映射
     * @return 不可修改的名称映射
     */
    private static Map<String, String> loadCountryMapping(JSONObject data) {
        if (Objects.isNull(data)) {
            throw new IllegalStateException("invalid project IPv4 country code resource");
        }
        Map<String, String> result = new HashMap<>(Math.max(16, data.size() * 2));
        for (String name : data.keySet()) {
            result.put(name, data.getString(name));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 清理国家名空白并应用明确的误填修正。
     * @param country 原国家标签，不能为空
     * @return 修正后的国家标签
     * @throws IllegalArgumentException 国家缺失或为空
     */
    public static String normalizeIpv4Country(String country) {
        if (Objects.isNull(country) || country.trim().isEmpty()) {
            throw new IllegalArgumentException("IPv4 country must not be empty");
        }
        String name = country.trim();
        return COUNTRY_CORRECTIONS.getOrDefault(name, name);
    }

    /**
     * 根据保留的国家语义补齐代码；未知或区域标签不推断具体国家。
     * @param country 国家或区域标签
     * @return 正式代码、与 wlzn 一致的 XK，或未知占位值 0
     */
    public static String resolveIpv4CountryCode(String country) {
        if (requiresZeroRegionCode(country)) {
            return "0";
        }
        return COUNTRY_CODES.getOrDefault(normalizeIpv4Country(country), "0");
    }

    /**
     * 判断已明确不能分配单一地区代码的标签；未知名称不自动归类。
     * @param country 国家或地区名称
     * @return 是否必须使用占位值 0
     */
    public static boolean requiresZeroRegionCode(String country) {
        return ZERO_CODE_LABELS.contains(normalizeIpv4Country(country).toLowerCase(Locale.ROOT));
    }

    /**
     * 判断国家列是否为需改写为 中国|地区 的港澳台标签。
     * @param country 已规范的国家或地区标签
     * @return 香港、澳门、台湾时为 true
     */
    public static boolean isChinaRegionLabel(String country) {
        return CHINA_REGION_LABELS.contains(country);
    }
}

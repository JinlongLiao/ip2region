"""按 Unicode CLDR release-48 的正式国家地区名称及显式旧别名解析 alpha-2。

来源与许可证见 data/国家名称代码映射.json 和 data/UNICODE-LICENSE.txt。
清理国家名两端空白，并修正已核对的城市误填；不按上游变化覆盖既有国家归属。
"""
import json
from pathlib import Path

COUNTRY_CODE_DATA = json.loads(
    (Path(__file__).resolve().parent / "data/国家名称代码映射.json").read_text(encoding="utf-8")
)
COUNTRY_CODES = COUNTRY_CODE_DATA["名称代码"]


# 自维护 103.155.250.0–103.155.251.255 的国家误填城市；wlzn f8b0792 同区间为 Indonesia/ID。
COUNTRY_NAME_CORRECTIONS = COUNTRY_CODE_DATA["已确认国家字段修正"]


def normalize_country_name(country):
    """清理国家标签空白，仅应用已明确核对的国家误填修正；区域与歧义标签保持。"""
    country = country.strip()
    return COUNTRY_NAME_CORRECTIONS.get(country, country)


def resolve_country_code(country):
    """返回正式 alpha-2；未知、跨国区域或有歧义的标签返回占位值 0。"""
    return "0" if requires_zero_region_code(country) else COUNTRY_CODES.get(normalize_country_name(country), "0")


def normalize_china_region(country, province):
    """港澳台与 Java IpAreaNormalizer 一致改写为 中国|地区；其余国家和省原样返回。"""
    if country in COUNTRY_CODE_DATA["归入中国地区标签"]:
        return "中国", country
    return country, province


def requires_zero_region_code(country):
    """明确区域与歧义标签强制归零；不将未收录国家当成区域。"""
    return normalize_country_name(country).casefold() in {
        name.casefold() for name in COUNTRY_CODE_DATA["强制零地区代码标签"]
    }

#!/usr/bin/env python3
"""将旧维护库迁移为新版七列格式，优先按国家名称补码，未知名称再按上游交集核验。"""
import argparse
import collections
import ipaddress
from pathlib import Path

from ipv4_output_files import validate_independent_output, write_ipv4_output_atomically
from ipv4_country_codes import normalize_china_region, normalize_country_name, resolve_country_code, requires_zero_region_code


def read_segments(path):
    """读取并校验完整、有序、连续的 IPv4 文本；错误时拒绝生成结果。"""
    rows = []
    previous = -1
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        fields = line.split("|")
        if len(fields) != 7:
            raise ValueError(f"{path}:{number}: 预期七列")
        start, end = (int(ipaddress.IPv4Address(value)) for value in fields[:2])
        if start != previous + 1 or end < start:
            raise ValueError(f"{path}:{number}: 区间断档、重叠或倒置")
        rows.append((start, end, fields))
        previous = end
    if previous != 0xFFFFFFFF:
        raise ValueError(f"{path}: 未覆盖完整 IPv4 范围")
    return rows


def migrate_ipv4_source(legacy, upstream, destination):
    """明确名称优先；未知名称仅在上游交集国家一致且代码唯一时补码。"""
    validate_independent_output(destination, legacy, upstream)
    original, reference = read_segments(legacy), read_segments(upstream)
    result, counts = [], collections.Counter()
    cursor = 0
    for start, end, fields in original:
        if fields[3] != "0":
            raise ValueError("旧 code 不是占位值 0，需人工确认后迁移")
        while reference[cursor][1] < start:
            cursor += 1
        index, codes, matched = cursor, set(), True
        while index < len(reference) and reference[index][0] <= end:
            region = reference[index][2]
            matched = matched and region[2] == fields[2] and region[6] not in ("", "0")
            codes.add(region[6])
            index += 1
        # 港澳台改写为 中国|地区 后再补码，与 Java IpAreaNormalizer 一致。
        country, province = normalize_china_region(normalize_country_name(fields[2]), fields[4])
        code = resolve_country_code(country)
        if code == "0" and not requires_zero_region_code(fields[2]):
            code = next(iter(codes)) if matched and len(codes) == 1 else "0"
        counts["已补码" if code != "0" else "保留0"] += 1
        result.append("|".join(fields[:2] + [country, province] + fields[5:] + [code]))
    write_ipv4_output_atomically(destination, "\n".join(result) + "\n", legacy, upstream)
    print(dict(counts), "总段数", len(result))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("legacy", type=Path)
    parser.add_argument("upstream", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    migrate_ipv4_source(args.legacy, args.upstream, args.destination)

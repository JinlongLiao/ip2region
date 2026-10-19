#!/usr/bin/env python3
"""校验新版 IPv4 文本并按自维护国家名称补齐正式代码；只写独立输出文件。"""
import argparse
import collections
import ipaddress
from pathlib import Path

from ipv4_output_files import validate_independent_output, write_ipv4_output_atomically
from ipv4_country_codes import normalize_china_region, normalize_country_name, resolve_country_code


def complete_ipv4_country_codes(source, destination):
    """全量校验后写出；修正明确国家误填与代码，港澳台改写为 中国|地区，其余字段保留，输出未知标签计数。"""
    validate_independent_output(destination, source)
    lines, unresolved, changed = [], collections.Counter(), 0
    previous = -1
    for number, line in enumerate(source.read_text(encoding="utf-8").splitlines(), 1):
        fields = line.split("|")
        if len(fields) != 7:
            raise ValueError(f"{source}:{number}: 预期新版七列")
        start, end = (int(ipaddress.IPv4Address(value)) for value in fields[:2])
        if start != previous + 1 or end < start:
            raise ValueError(f"{source}:{number}: 区间断档、重叠或倒置")
        fields[2], fields[3] = normalize_china_region(normalize_country_name(fields[2]), fields[3])
        code = resolve_country_code(fields[2])
        if code == "0":
            unresolved[fields[2]] += 1
        changed += fields[6] != code
        fields[6] = code
        lines.append("|".join(fields))
        previous = end
    if previous != 0xFFFFFFFF:
        raise ValueError("未覆盖完整 IPv4 范围")
    write_ipv4_output_atomically(destination, "\n".join(lines) + "\n", source)
    print("总段数", len(lines), "修改代码段数", changed, "保留0", sum(unresolved.values()))
    print("无法唯一映射的标签", dict(unresolved))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    complete_ipv4_country_codes(args.source, args.destination)

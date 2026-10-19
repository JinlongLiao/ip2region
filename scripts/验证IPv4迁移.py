#!/usr/bin/env python3
"""核对旧原件哈希、逐行字段迁移和 IPv4 xdb 的完整地址空间映射，不修改文件。"""
import hashlib
import ipaddress
import struct
from pathlib import Path

from ipv4_country_codes import normalize_china_region, normalize_country_name, resolve_country_code

ROOT = Path(__file__).resolve().parents[1]


def read_xdb_segments(path):
    """解码本仓库版本 2/3、四字节指针的 IPv4 索引，并验证连续覆盖。"""
    content = path.read_bytes()
    version = struct.unpack_from("<H", content)[0]
    if version == 3:
        assert struct.unpack_from("<HH", content, 16) == (4, 4)
    else:
        assert version == 2
    start, end = struct.unpack_from("<II", content, 8)
    assert (end - start) % 14 == 0 and end + 14 == len(content)
    previous = -1
    for offset in range(start, end + 1, 14):
        begin, finish, length, pointer = struct.unpack_from("<IIHI", content, offset)
        assert begin == previous + 1 and finish >= begin
        assert pointer + length <= start
        yield begin, finish, content[pointer:pointer + length].decode("utf-8")
        previous = finish
    assert previous == 0xFFFFFFFF


def read_text_segments(path):
    """逐行读取有界区间及完整 region，字段合法性由迁移脚本及逐行比对验证。"""
    for line in path.read_text(encoding="utf-8").splitlines():
        start, end, region = line.split("|", 2)
        yield int(ipaddress.IPv4Address(start)), int(ipaddress.IPv4Address(end)), region


def assert_same_mapping(left, right):
    """按交集比较完整区间映射，避免仅检查抽样点而遗漏段内差异。"""
    first, second = next(left, None), next(right, None)
    while first and second:
        start, end = max(first[0], second[0]), min(first[1], second[1])
        assert start <= end and first[2] == second[2], (first, second)
        first_end, second_end = first[1], second[1]
        if first_end <= second_end:
            first = next(left, None)
        if second_end <= first_end:
            second = next(right, None)
    assert first is None and second is None


def verify_ipv4_migration():
    """校验冻结原件、719901 段字段保持（港澳台按 中国|地区 改写），以及旧库和新库分别与其文本一致。"""
    data = ROOT / "data"
    for name, expected in {
        "ip.merge.txt": "ef0b206a5803631ac1c0834f54da781a4592c00c6c14f02a6246fc1f80ddc1be",
        "ip2region.xdb": "9a7629d8cdb3e7b3308068a3adb4175a3a3c81096072d3b65f5c82d21e64a504",
    }.items():
        assert hashlib.sha256((data / name).read_bytes()).hexdigest() == expected, name
    old = (data / "ip.merge.txt").read_text(encoding="utf-8").splitlines()
    new = (data / "ipv4_source.txt").read_text(encoding="utf-8").splitlines()
    assert len(old) == len(new) == 719901
    for before, after in zip(old, new):
        before, after = before.split("|"), after.split("|")
        assert len(before) == len(after) == 7
        country, province = normalize_china_region(normalize_country_name(before[2]), before[4])
        assert before[:2] + [country, province] + before[5:] == after[:6]
        assert after[6] == resolve_country_code(after[2]), after
    for source, binary in [("ip.merge.txt", "ip2region.xdb"), ("ipv4_source.txt", "ip2region_v4.xdb")]:
        assert_same_mapping(read_text_segments(data / source), read_xdb_segments(data / binary))
    print("通过：旧原件哈希、719901 段区间与省市ISP保留及明确国家修正、旧库与新库全 IPv4 空间映射一致")


if __name__ == "__main__":
    verify_ipv4_migration()

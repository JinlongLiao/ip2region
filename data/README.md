:globe_with_meridians: [中文简体](README_zh.md) | [English](README.md)

# source data
1. `ipv4_source.txt`: ipv4 source data including all the ipv4 segments.
2. `ipv6_source.txt`: ipv6 source data including all the ipv6 segments.

# xdb binary
1. `ip2region_v4.xdb`: ipv4 xdb binary file.
2. `ip2region_v6.xdb`: ipv6 xdb binary file.

## 本分支自维护数据说明

`ipv4_source.txt` 与 `ip2region_v4.xdb` 使用 old_version 的自维护 IPv4 数据，已迁移为新版字段顺序；新版应用加载 `ip2region_v4.xdb`。`ip.merge.txt` 与 `ip2region.xdb` 仅保留为迁移前原件和回归基线，日常维护统一使用 `ipv4_source.txt`。IPv6 使用 wlzn 原数据。详细规则见 [合并迁移与数据清洗验收记录](../docs/合并迁移与数据清洗验收记录.md)。

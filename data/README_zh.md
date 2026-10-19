:globe_with_meridians: [中文简体](README_zh.md) | [English](README.md)

# 原始数据
1. `ipv4_source.txt`: ipv4 原始数据，包含了全部的 ipv4 数据段。
2. `ipv6_source.txt`: ipv6 原始数据，包含了全部的 ipv6 数据段。

# xdb 二进制
1. `ip2region_v4.xdb`: ipv4 xdb 二进制文件。
2. `ip2region_v6.xdb`: ipv6 xdb 二进制文件。

# 中文地域名称标准
1. 省份：新疆/西藏等自治区使用短称，香港/澳门等特别行政区使用长称，直辖市携带“市”行政单位，其他的统一使用带”省“的全称，。
2. 城市：自治地区携带”地区“行政单位，例如：”阿里地区“，自治州使用简称，例如：”湘西“，其他都携带”市“行政单位，其他的统一使用带”市“的全称，。

## 本分支自维护数据说明

`ipv4_source.txt` 与 `ip2region_v4.xdb` 使用 old_version 的自维护 IPv4 数据，已迁移为新版字段顺序；新版应用加载 `ip2region_v4.xdb`。`ip.merge.txt` 与 `ip2region.xdb` 仅保留为迁移前原件和回归基线，日常维护统一使用 `ipv4_source.txt`。IPv6 使用 wlzn 原数据。详细规则见 [合并迁移与数据清洗验收记录](../docs/合并迁移与数据清洗验收记录.md)。

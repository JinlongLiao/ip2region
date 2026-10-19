# IPv6 解析与区间合并说明

本功能新增在 `maker/java` 项目源码中，独立于现有 IPv4 补丁合并。采用已确认的显式起止区间，不从单个样本推断 /64 或其他前缀，不采集真实供应方数据。

## 数据格式与覆盖规则

源与补丁都是新版七列：

```text
起始IPv6|结束IPv6|国家或地区|省|市|ISP|地区代码
2001:db8::10|2001:db8::20|香港|香港|葵涌|Example ISP|HK
```

端点是包含边界的闭区间。补丁范围内替换全部五列归属，包括 ISP；范围外原归属完全保留。与 IPv4 样本 CSV 不同，IPv6 不使用样本所属 /24，不接受在本入口传入 IPv4 或 CIDR。

源必须按起点递增且无重叠，允许有空洞、允许不覆盖整个 IPv6 空间。补丁按起点排序后必须互不重叠，每条补丁必须完整位于已有连续覆盖内；越界、跨空洞或重复覆盖全部拒绝，不自行补洞、不猜测冲突优先级。

成功后合并地址相邻且完整五列相同的记录，不跨空洞合并。输出必须独立于源和补丁；所有解析、校验及计算完成后才原子发布，失败保留输入及已有输出。

## 解析与地区代码

`Ipv6AddressParser` 使用 BigInteger 保存无符号 128 位地址，不丢失高 64 位。支持完整八组、一次双冒号压缩、大小写十六进制以及末尾嵌入 IPv4 的 IPv6。拒绝主机名、作用域标识、方括号、CIDR、首尾空白和非法分组，不执行 DNS。

地址输出使用小写、移除组内前导零，压缩最左侧最长的至少两组零；IPv4 嵌入形式输出为完整 IPv6 十六进制形式并保留 16 字节。参考 [RFC 4291 §2.2](https://www.rfc-editor.org/rfc/rfc4291#section-2.2) 和 [RFC 5952 §4](https://www.rfc-editor.org/rfc/rfc5952#section-4)。

源记录的国家或地区、英文名称和既有地区代码原样保留，避免将 wlzn 数据中的 Japan/JP 等误清为 0。补丁中的明确中文名称复用项目映射规则：代码为 0 时补齐，非零代码与明确名称矛盾时拒绝；英文名称使用补丁显式代码。香港、澳门、台湾保留 HK/MO/TW，科索沃采用 XK，大洲及已确认歧义标签为 0。

制库器的 IPv6 地址入口也接入严格解析，避免 JDK 将 IPv4 映射的 IPv6 自动折叠为四字节。IPv4 原解析分支保持不变。

## 项目入口

- `merge/ipv6/Ipv6AddressParser.java`：地址数值、字节和规范文本转换。
- `merge/ipv6/Ipv6RegionSegment.java`：不可变 IPv6 源记录、补丁及范围拆分模型。
- `merge/ipv6/Ipv6MergeApp.java`：独立文件合并入口与 Java API，使用有序区间索引。

在项目根目录运行：

```bash
mvn -o -f maker/java/pom.xml clean package

# 显式区间合并，只写独立候选文本
java -cp maker/java/target/ip2region-maker-3.1.0.jar org.lionsoul.ip2region.merge.ipv6.Ipv6MergeApp data/ipv6_source.txt /tmp/ipv6_patch.txt /tmp/ipv6-merged.txt

# 从候选文本生成独立候选 IPv6 库
java -jar maker/java/target/ip2region-maker-3.1.0.jar --src=/tmp/ipv6-merged.txt --dst=/tmp/ip2region_v6.candidate.xdb --version=ipv6
```

项目代码中也可直接调用 `Ipv6MergeApp.mergeIpv6SourceFiles(sourcePath, patchPath, outputPath)`，无需通过命令行调用。

## IPv4 使用方式修正

`IpLoopTest` 无参数运行已恢复代码配置：起点 `36.6.27.0`、终点 `36.192.160.255`，可直接在源码修改。默认写当前工作目录的外部 `merget.txt`；三个命令行参数仅为可选覆盖，不再是必填。已用本地查询桩验证无参数运行首个样本，无真实网络请求或已有输出覆盖。

## 验证边界

测试覆盖 128 位最小/最大值、跨 64 位边界、压缩格式、与 JDK 纯字节地址表示的随机对照、非法输入、源段拆分、跨段覆盖、相邻合并、原地区代码保留、空洞/越界/重叠拒绝，以及合并文本到 IPv6 xdb 索引与归属的一致性。

真实 IPv6 数据验证仅在临时目录执行，原 `data/ipv6_source.txt` 与 `data/ip2region_v6.xdb` 不覆盖。未提交或推送；当前也没有对暂存区执行新增 Git 写操作。

## 本轮验收结果

- 制库模块干净构建与打包通过：40 项测试，0 失败、错误、跳过，其中新增 IPv6 测试 11 项，并包含 IPv4 无参数源码配置入口回归。
- 查询模块 31 项测试通过；Python 5 项规则测试通过。
- 完整 670732 段 IPv6 源应用 1000 条区间补丁，命令耗时约 3.05 秒；原文与结果逐区间交集核对通过，补丁范围外完整归属保持不变。
- 从完整合并文本生成候选 IPv6 xdb，736952 个文本/索引交集映射一致，候选库 37395665 字节；仅位于临时目录。
- 使用现有 Java 查询器逐条验证合并后源段的首、中、尾地址；文件及向量缓存每 1000 段抽样对照。
- `data/ipv6_source.txt` 与 `data/ip2region_v6.xdb` 逐字节仍等于 wlzn 原件；本次工作区差异 `git diff --check` 通过。

本轮没有新增 Git 写操作。新增 IPv6 源码、测试、文档和恢复的 IPv4 源码配置入口目前仅在工作区，尚未暂存、提交或推送。

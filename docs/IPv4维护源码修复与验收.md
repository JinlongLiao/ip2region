# IPv4 维护源码修复与验收

本次修复位于 `v-wlzn2.0` 的项目源码与构建资源中。通过正常构建验证产物，不修改运行中的 jar。旧 IPv4 文本与旧 xdb 原件保留，日常维护入口为 `data/ipv4_source.txt`，应用加载 `data/ip2region_v4.xdb`。

## 输入格式必须明确

两种源数据都是七列，不能仅凭列数自动识别。维护命令必须声明 `--source-format=legacy` 或 `--source-format=latest`，缺少声明立即报错且不写文件。

- legacy：起始 IP、结束 IP、国家或地区、旧 code、省、市、ISP。
- latest：起始 IP、结束 IP、国家或地区、省、市、ISP、地区代码。

`RegionItem` 保留新版构造入口，并提供显式格式构造入口。记录地址、起止顺序及地区代码列会校验。旧数据仅在明确指定 legacy 时迁移，迁移结果统一输出 latest；不存在对所有七列数据可靠的自动识别规则。

## 项目级地区代码规则

Java 源码 `Ipv4CountryNormalizer` 与 Python 迁移工具共享 `scripts/data/国家名称代码映射.json`，Maven 将该文件及 Unicode 许可证作为构建资源。维护代码不依赖 Python 进程，也不改写 jar。

明确国家或地区使用对应代码；大洲、跨国区域及歧义标签使用 0。IPv4 采集器沿用历史约定：国家为香港、澳门、台湾时改写为 `中国|香港`、`中国|澳门`、`中国|台湾`（代码 CN），与存量数据一致。映射表中的 HK/MO/TW 仅在国家列本身为港澳台时使用。科索沃 XK 与 wlzn 保持一致。字段名 `country` 及已有配置键保持兼容，代码注释统一称“地区代码”。

补丁改变国家或地区后直接重新解析地区代码，不再一律清为 0；清理或读取维护源时也补齐明确代码，避免重新制库后恢复成大量 0。地域文本仅清理空白、已确认误填及原有省市简称规则，不能根据上游当前 IP 归属覆盖自维护内容。

## 源码修复

1. `MergeTest` 对源区间建立 TreeMap 索引，每个补丁只定位和拆分相交区间；全部操作完成并验证后原子写出。保留范围外归属与原 ISP，不覆盖补丁文件。相同 /24 的重复补丁仍按排序后的顺序依次覆盖；未新增不明确的冲突优先级。
2. `IpLoopTest` 无参数时使用源码配置的原范围 `36.6.27.0–36.192.160.255`，写当前工作目录 `merget.txt`；可选三个参数临时指定起止 IPv4 与外部 CSV 输出路径，不再写 classpath 的 merget.txt。先校验范围及输出目录，再采集到临时文件；异常或中断不发布半份结果。long 步进避免到达 IPv4 最大值后回绕。保留实际“所有非空归属”行为，删除与代码不一致的“仅安徽”说明；未增加省份过滤。
3. `ReplaceTest` 显式读取源格式，仅清理省、市末尾行政单位，按共享规则修复地区代码。拒绝覆盖输入，经完整处理后原子发布独立输出。
4. `Maker` 和 `MakerApp` 拒绝输入输出同文件，包括链接到同一文件的情况。底层 Maker 同样先写同目录候选，只有 init/start 完整成功后 end 才原子发布。close 清理失败候选；旧正式库在失败时保持不变。文件系统不支持原子替换时失败，不退回可能损坏旧文件的非原子覆盖。
5. `MakerApp` 遇到参数错误或制库失败，输出参数、异常类型、message、完整堆栈并传播异常，命令行非零退出。主入口使用 try-with-resources，失败关闭句柄。
6. 原有采集器归属规范化（`IpAreaNormalizer`）保持历史行为：香港、澳门、台湾改写为中国并将原标签移入省列。

公共工具已核对并复用 StringUtil 的空白判断及 StreamUtils 的 UTF-8 资源读取；原子发布为维护流程专用职责，现有流拷贝工具没有对应原子替换契约。

## 使用方法

以下在项目根目录运行。所有维护输出先使用候选文件；是否替换正式文本由调用方明确决定。

```bash
# 正常构建项目
mvn -o -f maker/java/pom.xml clean package

# 从旧数据原件迁移并应用实际补丁，保留旧原件
java -cp maker/java/target/ip2region-maker-3.1.0.jar org.lionsoul.ip2region.merge.MergeTest data/ip.merge.txt /tmp/merget.csv /tmp/ipv4-merged.txt --source-format=legacy

# 在新版源上应用补丁
java -cp maker/java/target/ip2region-maker-3.1.0.jar org.lionsoul.ip2region.merge.MergeTest data/ipv4_source.txt /tmp/merget.csv /tmp/ipv4-merged.txt --source-format=latest

# 清理新版文本并补齐地区代码
java -cp maker/java/target/ip2region-maker-3.1.0.jar org.lionsoul.ip2region.merge.ReplaceTest data/ipv4_source.txt /tmp/ipv4-cleaned.txt --source-format=latest

# 采集会实际访问供应方；起止范围及输出必须明确
java -cp maker/java/target/ip2region-maker-3.1.0.jar org.lionsoul.ip2region.merge.IpLoopTest 36.6.27.0 36.6.28.255 /tmp/merget.csv

# 从指定候选源生成独立候选库
java -jar maker/java/target/ip2region-maker-3.1.0.jar --src=/tmp/ipv4-cleaned.txt --dst=/tmp/ip2region_v4.candidate.xdb --version=ipv4
```

## 验证边界

自动测试使用本地查询桩验证采集到合并、清理和制库的完整流程，不请求真实 IP 供应方。另执行完整自维护数据与 10000 条补丁的离线批量验证，并校验输出全空间连续性与补丁命中结果。单次本机耗时仅作为本轮样例证据，不代表其他机器或供应方在线采集性能。

源码、完整数据、制库结果、磁盘与暂存区一致性均需分别检查。未推送、部署或验收生产服务。

## 本轮实际验收结果

- 制库模块 `mvn -o -f maker/java/pom.xml clean package`：28 项测试，0 失败、错误、跳过；正常构建成功。
- 查询模块 `mvn -o -f binding/java/pom.xml test`：31 项测试，0 失败、错误、跳过；包括全部自维护区间首、中、尾与三种缓存策略双栈并发查询。
- Python 5 项规则测试通过；完整 IPv4 映射校验通过。
- 全量 719901 段源数据、10000 条补丁，执行合并命令约 1.794 秒；输出完整空间逐区间交集核对通过，范围外数据与原 ISP 保留，目标范围省市与地区代码均符合补丁。
- 实际 CLI 同路径制库返回非零，输入字节不变，错误带参数和完整堆栈。源码构建产物成功生成完整 IPv4 库，文本与库在全部地址空间映射一致，已更新 `data/ip2region_v4.xdb`。
- 原始 `data/ip.merge.txt` 与 `data/ip2region.xdb` 哈希保持冻结基线。当前文本中明确国家和地区的代码均已补齐，大洲及歧义项仍为 0。
- 维护源码与测试的差异空白检查通过。完整合并仍含 wlzn 自带的历史尾随空白，未全仓格式化。

以上不包含真实供应方在线可用性测试；采集逻辑使用本地桩完成全链路验证。已暂存的改动仅供审查，未创建合并提交、推送或部署。最终发现全局 *.json 忽略规则遗漏地区代码映射；工作区已增加单文件例外，映射文件、忽略规则与本说明的最终同步暂存需用户明确授权（自动审批以 AGENTS.md 的 Git 写操作授权要求拦截）。

## 2026-10-19 老李复审问题修复

本次仅修改维护源码、共享规则、构建配置及测试，不改正式 IPv4/IPv6 文本与 xdb，不执行 Git 暂存、提交或推送。

- 大洲/跨国区域/歧义标签改由 JSON `强制零地区代码标签` 共享，Java IPv6 补丁不再使用私有不完整列表；Python 旧数据迁移也不会从错误上游给这些标签继承非零代码。保留港澳台原字段及 HK/MO/TW，未知英文标签不猜测国家。
- Python 两入口检查目标与全部输入的真实路径及文件身份，拒绝硬链接别名；同目录临时文件完整写入、同步后原子替换。写入失败保留原输入和已有输出并清理临时文件。维护期间要求独占相关路径，文件权限采用临时文件权限。
- Java 正常构建增加与现有 SLF4J 1.7 API 兼容的命令行日志绑定。供应方失败日志记录 IP、GET 地址、HTTP 状态、完整响应及耗时；传输和解析异常附带类型、message、完整堆栈。失败样本不生成补丁。
- `IpLoopTest` 代码内默认范围仍为 `36.6.27.0`–`36.192.160.255`，无参数入口保持；IPv4 `/24` 与 IPv6 显式起止补丁边界不变。

验证：`mvn -o -f maker/java/pom.xml package` 成功，制库模块 42 项测试通过；`python3 -m unittest discover -s scripts -p 'test_*.py'` 9 项通过，包括硬链接拒绝与子进程真实文件大小限制导致的中途写失败。供应方日志测试仅注入离线响应与 IOException，不请求真实供应方。`git diff --check` 通过；未进行在线采集或生产发布。构建仍提示原有 compiler 插件缺少版本及依赖重复 module-info 元数据警告，与本次故障无关。

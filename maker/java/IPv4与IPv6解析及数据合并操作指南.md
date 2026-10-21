# IPv4 与 IPv6 解析及数据合并操作指南

适用于本项目 `maker/java` 源码中的数据维护功能。本文命令统一从仓库根目录执行；在 IDE 中运行时也应明确工作目录，避免相对路径指向不同文件。

## 1. 文件与入口

| 用途 | IPv4 | IPv6 |
| --- | --- | --- |
| 日常维护文本 | `data/ipv4_source.txt`，基于自维护 old_version 数据 | `data/ipv6_source.txt`，当前基于 wlzn 数据 |
| 应用加载的库 | `data/ip2region_v4.xdb` | `data/ip2region_v6.xdb` |
| 地址解析 | `merge.IpConveter` | `merge.ipv6.Ipv6AddressParser` |
| 文本记录解析 | `merge.RegionItem` | `merge.ipv6.Ipv6RegionSegment` |
| 采集样本 | `merge.IpLoopTest`，会请求供应方 | 当前没有新增 IPv6 网络采集器 |
| 合并入口 | `merge.MergeTest` | `merge.ipv6.Ipv6MergeApp` |
| 省市后缀清理 | `merge.ReplaceTest` | 没有独立的 IPv6 后缀清理入口 |
| 制库入口 | `MakerApp`，`--version=ipv4` | `MakerApp`，`--version=ipv6` |

表中类名都位于 `org.lionsoul.ip2region` 包下。名称带 `Test` 的上述 main 类是实际维护工具，运行可能采集网络数据或写文件，并非无副作用的单元测试。

`data/ip.merge.txt` 和 `data/ip2region.xdb` 是迁移前 IPv4 原件及回归基线。日常修改新版文本不会自动同步这些旧文件，修改旧文件也不会自动更新新版库。

地区代码共享规则位于 [scripts/data](../../scripts/data/地区代码映射维护说明.md)。

## 2. 构建与执行位置

```bash
cd /Users/thinking/workspace/work/wlzn_base_lib/ip2region
mvn -f maker/java/pom.xml clean package
mkdir -p /tmp/ip2region-manual
```

本分支 POM 当前产物为 `maker/java/target/ip2region-maker-3.1.0.jar`，版本变化时同步调整下文命令。依赖和插件齐全时可加 Maven `-o` 离线构建；缺少依赖时沿用用户 `~/.m2/settings.xml` 联网解析，不覆盖 `localRepository`。

修改 Java 源码或地区代码规则后应重新构建，再运行新构建产物。本文所有演示输出都在 `/tmp/ip2region-manual`，不会替换正式数据。

## 3. 文本与地址解析格式

### 3.1 新版七列

IPv4、IPv6 新版文本均按以下顺序，使用 `|` 分隔：

```text
起始IP|结束IP|国家或地区|省|市|ISP|地区代码
1.0.1.0|1.0.3.255|中国|福建|福州|电信|CN
2001:db8::|2001:db8::ffff|Japan|Tokyo|Tokyo|Example ISP|JP
```

地址区间包含两端。缺省地区代码为 `0`；IPv4 采集器将香港、澳门、台湾改写为 `中国|香港`、`中国|澳门`、`中国|台湾`（`CN`）；IPv6 维护工具保留港澳台原标签及 `HK/MO/TW`；科索沃使用 `XK`，大洲及歧义标签使用 `0`。

旧 IPv4 也是七列，但顺序为：

```text
起始IPv4|结束IPv4|国家或地区|旧code|省|市|ISP
1.0.1.0|1.0.3.255|中国|0|福建|福州|电信
```

旧 `code` 在冻结的自维护数据中全部为 `0`，不能当作有效地区代码。Java IPv4 清理、合并必须明确指定 `--source-format=latest` 或 `--source-format=legacy`，且该参数放在最后。不要凭“都是七列”混用。

### 3.2 Java 中直接解析

以下片段可放入自己的维护方法，不涉及网络或文件写入：

```java
import java.math.BigInteger;
import org.lionsoul.ip2region.merge.IpConveter;
import org.lionsoul.ip2region.merge.RegionItem;
import org.lionsoul.ip2region.merge.ipv6.Ipv6AddressParser;
import org.lionsoul.ip2region.merge.ipv6.Ipv6RegionSegment;

long ipv4Number = IpConveter.ipToLong("1.0.1.42");
String ipv4Address = IpConveter.longToIp(ipv4Number);
RegionItem ipv4Segment = new RegionItem(
        "1.0.1.0|1.0.3.255|中国|0|福建|福州|电信", "legacy");

BigInteger ipv6Number = Ipv6AddressParser.parseIpv6Address("2001:db8::42");
String ipv6Address = Ipv6AddressParser.formatIpv6Address(ipv6Number);
Ipv6RegionSegment ipv6Segment = Ipv6RegionSegment.parseIpv6SourceSegment(
        "2001:db8::|2001:db8::ffff|Japan|Tokyo|Tokyo|Example ISP|JP");
```

IPv4 使用无符号 32 位数值，范围 `0..2^32-1`，禁止非法四段或越界数值。IPv6 使用 `BigInteger` 保留完整 128 位；支持压缩、大小写及 IPv4 嵌入形式，拒绝主机名、作用域 `%en0`、方括号、CIDR 和带空白的端点。IPv6 解析不执行 DNS；输出规范的小写压缩文本，数值不变。维护工具保留 IPv4 映射类 IPv6 的 128 位表示，但绑定查询模块有自己的解析和路由逻辑；这类地址还需在实际消费方确认，不能默认所有客户端字符串入口都会按 IPv6 查询。

## 4. IPv4：采集、合并、清理、制库

### 4.1 原有代码配置范围与采集

`IpLoopTest` 无参数运行时读取源码中的三项配置：

```java
DEFAULT_START_IP = "36.6.27.0";
DEFAULT_END_IP = "36.192.160.255";
DEFAULT_PATCH_OUTPUT = Paths.get("merget.txt");
```

可直接在源码修改起止范围及输出位置，然后在 IDE 中无参数运行。默认文件在进程的当前工作目录，不是 jar 或 classpath 里的资源；每隔 256 个地址取一个样本。实际行为保留所有非空归属，没有额外的“仅安徽”筛选。默认查询器为百度供应方，间隔 50 毫秒。

以下命令会请求真实供应方，不属于本文离线演示：

```bash
# 按源码配置运行
java -cp maker/java/target/ip2region-maker-3.1.0.jar org.lionsoul.ip2region.merge.IpLoopTest

# 可选：临时覆盖起止范围及输出，不必修改源码
java -cp maker/java/target/ip2region-maker-3.1.0.jar org.lionsoul.ip2region.merge.IpLoopTest 36.6.27.0 36.6.28.255 /tmp/ip2region-manual/ipv4-collected.csv
```

采集结果为八列 CSV：

```text
样本IPv4,0,0,0,国家或地区,省,市,ISP
1.0.2.42,0,0,0,中国,安徽省,合肥市,供应方ISP
```

合并器兼容没有末列 ISP 的历史七列 CSV。字段内允许逗号和双引号：采集器自动为字段加引号，并将内部双引号写成两个双引号，合并器配套解码。字段仍不能含换行或竖线，以保持单行补丁及七列源库格式。采集到临时文件，完成后原子发布；返回空的样本跳过；查询器向外抛异常或线程中断时不发布半份结果。供应方适配器若将失败转换成空结果，则仍按跳过处理，需同时检查采集日志和样本数量。本项目没有自动重试失败样本的持久化清单。

### 4.2 离线合并演示

下面构造独立的演示源和补丁，省市仅用于说明覆盖效果，不是对真实 IP 归属的判断：

```bash
cat > /tmp/ip2region-manual/ipv4-source.txt <<'EOF'
1.0.0.0|1.0.0.255|澳大利亚|维多利亚|墨尔本|原ISP|AU
1.0.1.0|1.0.3.255|中国|福建|福州|电信|CN
EOF
cat > /tmp/ip2region-manual/ipv4-patch.csv <<'EOF'
1.0.2.42,0,0,0,中国,安徽省,合肥市,供应方ISP
EOF
java -cp maker/java/target/ip2region-maker-3.1.0.jar org.lionsoul.ip2region.merge.MergeTest /tmp/ip2region-manual/ipv4-source.txt /tmp/ip2region-manual/ipv4-patch.csv /tmp/ip2region-manual/ipv4-merged.txt --source-format=latest
```

合并行为：

- 样本 `1.0.2.42` 覆盖其所属 `/24` 与源范围的交集，即本例 `1.0.2.0..1.0.2.255`。
- 目标范围修改国家或地区、省、市，地区代码按共享规则重新解析；CSV 有效 ISP 覆盖目标范围的原 ISP；运营商只保留第一个英文逗号前的内容并去掉两侧空格；“中国电信/中国移动/中国联通/中国广电”去掉开头“中国”。七列补丁或 ISP 为空白、`0` 时保留源 ISP。
- 两侧仍保留福建/福州；澳大利亚那段不变。拆分后无断档或重叠。
- 源必须有序、无重叠且连续，但可只是全空间的连续子区间。样本本身超出源范围会失败。
- 补丁按样本地址排序。同一 `/24` 的多个样本仍会依次应用，不进行来源可信度判断；实际补丁应先核对冲突。

指定第三个路径时源不变；**省略输出路径会覆盖源文本**。日常操作建议总是使用独立候选输出。

维护真实新版数据时，将演示源换成 `data/ipv4_source.txt`，补丁换成实际采集结果。读取旧原件时使用：

```bash
java -cp maker/java/target/ip2region-maker-3.1.0.jar org.lionsoul.ip2region.merge.MergeTest data/ip.merge.txt /tmp/ip2region-manual/ipv4-patch.csv /tmp/ip2region-manual/ipv4-from-legacy.txt --source-format=legacy
```

legacy 输入合并后也统一输出 latest 顺序，不保留旧字段布局。

### 4.3 清理合并结果

```bash
java -cp maker/java/target/ip2region-maker-3.1.0.jar org.lionsoul.ip2region.merge.ReplaceTest /tmp/ip2region-manual/ipv4-merged.txt /tmp/ip2region-manual/ipv4-cleaned.txt --source-format=latest
```

`ReplaceTest` 仅移除省列末尾“省”和市列末尾“市”，同时按共享规则修正明确国家误填及地区代码；保留 ISP，不处理源中间的同名字符。示例目标行会成为：

```text
1.0.2.0|1.0.2.255|中国|安徽|合肥|供应方ISP|CN
```

输入输出必须不同，非法字段或地址时拒绝写出。此入口不是完整的地理归属准确性校验器，也不会靠网络查询确认地域是否正确。

### 4.4 生成 IPv4 候选库

```bash
java -jar maker/java/target/ip2region-maker-3.1.0.jar --src=/tmp/ip2region-manual/ipv4-cleaned.txt --dst=/tmp/ip2region-manual/ip2region_v4.candidate.xdb --version=ipv4
```

制库会先生成同目录候选文件，完整成功后才原子发布；失败保留已有目标。输入输出同文件或链接到同文件会失败，CLI 返回非零。正式维护时，源必须是你已确认的新版字段文本；制库器支持自定义 region，不会替你判断七列是旧格式还是新格式。

## 5. IPv6：解析后显式区间合并与制库

IPv6 当前只新增解析及区间合并，没有按供应方自动采集 IPv6 归属的入口。补丁需要人工整理或由外部明确来源生成，记录其完整起止范围与五列归属。

```bash
cat > /tmp/ip2region-manual/ipv6-source.txt <<'EOF'
2001:db8::|2001:db8::ffff|Japan|Tokyo|Tokyo|Old ISP|JP
EOF
cat > /tmp/ip2region-manual/ipv6-patch.txt <<'EOF'
2001:db8::10|2001:db8::20|香港|香港|葵涌|New ISP|0
EOF
java -cp maker/java/target/ip2region-maker-3.1.0.jar org.lionsoul.ip2region.merge.ipv6.Ipv6MergeApp /tmp/ip2region-manual/ipv6-source.txt /tmp/ip2region-manual/ipv6-patch.txt /tmp/ip2region-manual/ipv6-merged.txt
java -jar maker/java/target/ip2region-maker-3.1.0.jar --src=/tmp/ip2region-manual/ipv6-merged.txt --dst=/tmp/ip2region-manual/ip2region_v6.candidate.xdb --version=ipv6
```

结果为：

```text
2001:db8::|2001:db8::f|Japan|Tokyo|Tokyo|Old ISP|JP
2001:db8::10|2001:db8::20|香港|香港|葵涌|New ISP|HK
2001:db8::21|2001:db8::ffff|Japan|Tokyo|Tokyo|Old ISP|JP
```

IPv6 操作边界：

- 补丁范围内替换全部五列，包括 ISP；不会推断 `/64`，也不支持在该入口直接填 CIDR。
- 源有序、无重叠，可包含空洞；每条补丁必须完整落在已有连续覆盖内。越界或跨空洞会拒绝，不自动新增未知区间。
- 补丁先排序且必须互不重叠；重复、交叉覆盖会拒绝，不采用“最后一条优先”。
- 相邻且五列完全相同的记录会合并；不同地区代码或中间有空洞的记录不合并。
- 输出必须不同于源和补丁；成功前不覆盖原文件。无需 `--source-format`，IPv6 入口只接受上述新版七列。
- 源中的英文名称及原地区代码保留。补丁的明确中文名称可补码；英文名称应显式给出代码。非零代码与已知中文名称矛盾时拒绝，大洲及确认的歧义标签统一为 `0`。

在项目代码里可直接调用，不必启动命令行：

```java
import java.nio.file.Paths;
import org.lionsoul.ip2region.merge.ipv6.Ipv6MergeApp;

Ipv6MergeApp.mergeIpv6SourceFiles(
        Paths.get("data/ipv6_source.txt"),
        Paths.get("/tmp/ipv6-patch.txt"),
        Paths.get("/tmp/ipv6-merged.txt"));
```

## 6. 地区代码批量补齐与旧数据迁移

完整规则、配置字段及修改流程见 [地区代码映射维护说明](../../scripts/data/地区代码映射维护说明.md)。以下 Python 工具仅处理 IPv4，不用于 IPv6：

```bash
# 补齐当前完整 IPv4 文本的地区代码，只写独立输出
python3 scripts/补齐IPv4国家代码.py data/ipv4_source.txt /tmp/ip2region-manual/ipv4-codes.txt

# 初次迁移旧布局：依次传旧文本、真正的上游参考文本、独立输出
python3 scripts/迁移IPv4数据.py data/ip.merge.txt /tmp/upstream-ipv4-source.txt /tmp/ip2region-manual/ipv4-migrated.txt
```

这两项脚本要求输入从 `0.0.0.0` 连续覆盖到 `255.255.255.255`，因此不能拿本文的局部 IPv4 演示源运行。上游参考不能用已经迁移过的自维护 `data/ipv4_source.txt` 冒充；需从确认的 wlzn 提交提取原始参考。

脚本名称保留历史“国家代码”字样以保持命令兼容；本项目文档和字段语义均称“地区代码”。Java IPv4 维护会根据名称修复代码；读取外来英文 IPv4 文本前，应先确认名称映射，否则未收录名称可能输出 `0`。IPv6 源读取则保留原始名称和代码。

## 7. 校验与正式采用候选

```bash
mvn -o -f maker/java/pom.xml test
mvn -o -f binding/java/pom.xml test
python3 -m unittest discover -s scripts -p 'test_*.py'
```

现有 `binding` 测试默认使用仓库 `data` 下的库，**不会自动检验 `/tmp` 中任意候选库**。候选需要在实际调用方指定候选路径，并对补丁的首、中、尾及两侧原记录做查询对照，检查完整五列归属。

需要代码中查询候选时，使用独立的 binding/java 模块及其类路径。maker 与 binding 含同名 `xdb` 类型，不要把两个模块的全部类混在同一个运行类路径。制库文本字段与查询调用方的字段下标也要同时确认。

在独立的 binding/java 调用环境中，可用下列片段查询本文的 IPv6 演示候选库：

```java
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;

Searcher searcher = Searcher.newWithFileOnly(
        Version.IPv6, "/tmp/ip2region-manual/ip2region_v6.candidate.xdb");
try {
    System.out.println(searcher.search("2001:db8::10"));
    // 演示结果：香港|香港|葵涌|New ISP|HK
} finally {
    searcher.close();
}
```

IPv4 对照时使用 `Version.IPv4`、IPv4 候选路径及 `1.0.2.42`，本例应返回 `中国|安徽|合肥|供应方ISP|CN`。调用方必须关闭查询器；以上仅为维护时单线程查询示例，不代替业务并发服务配置。

冻结迁移基线还可执行：

```bash
python3 scripts/验证IPv4迁移.py
```

该脚本固定检查迁移前原件哈希及 719901 段迁移关系，适合验证冻结迁移结果；日常应用新补丁后，原件、段数和归属差异可能是预期变化，不能把该脚本当作所有未来数据版本的通用验收。

确认候选的区间、归属、地区代码和真实消费者查询结果后，再由维护者明确选择正式文本与正式 xdb 的替换。文本变化不会自动重建库，生成候选库也不会自动让服务切换路径。记录原文本、原库、候选及校验结果，保留可恢复副本。

## 8. 常见问题

| 现象 | 处理 |
| --- | --- |
| IPv4 报必须声明 source-format | 在最后添加 `--source-format=latest`；读取旧布局则用 `legacy` |
| 旧文本被读成省=0、地区代码=电信 | 使用了错误布局；明确 legacy，再输出新版候选 |
| Java 报缺失地区代码资源 | 检查 `scripts/data/国家名称代码映射.json` 存在并随项目交付，再正常构建 |
| 修改 JSON 后结果不变 | Java 重新构建并运行新进程；Python 重新启动进程 |
| IPv4 CSV 的 ISP 没覆盖原数据 | 八列补丁的有效 ISP 参与覆盖；缺列、空白或 0 保留源 ISP |
| IPv6 报跨空洞或超出源范围 | 核对补丁边界；不扩大源空间来掩盖错误 |
| IPv6 报补丁重叠 | 先整理冲突，输入互不重叠的区间 |
| 候选制库失败 | 查看非零退出及完整异常，保留原库；不要用空文件替换正式库 |
| 制库输出字段顺序不对 | 核对源布局；`--field-list` 是字段选择并排序，不能用于旧字段重排 |

进一步实现和验证记录：[IPv4维护源码修复与验收](../../docs/IPv4维护源码修复与验收.md)、[IPv6解析与区间合并说明](../../docs/IPv6解析与区间合并说明.md)。

## 失败诊断与安全输出补充

Java 构建产物包含兼容现有 SLF4J 1.7 的日志绑定；供应方 HTTP、业务错误以及传输、解析异常输出 IP、GET 地址、状态、响应和耗时，异常同时输出类型、message 与完整堆栈。错误采集结果返回空，不形成补丁。离线测试通过请求替身验证，无需连接真实供应方。

IPv6 补丁的大洲、跨国区域和明确歧义标签统一由 `scripts/data/国家名称代码映射.json` 的 `强制零地区代码标签` 归零，避免 Java 与 Python 各自维护不完整列表。源文件读取仍保留已有地区代码；补丁仅替换显式区间，不改变 IPv4 `/24` 语义或默认源码范围。

Python 迁移和补码入口拒绝输入同路径、符号链接及硬链接别名，完成临时文件写入后才原子发布输出。磁盘写失败保留原输出和输入。

### 合并与制库日志

`MergeTest` 使用 SLF4J，读取 `src/main/resources/simplelogger.properties`，默认向标准输出记录输入输出路径、源段数、补丁去重数量、每 1000 条及末条补丁进度、输出段数、文件大小和耗时。失败记录参数、异常原因及完整堆栈，并继续抛出异常；逐条补丁详情使用 DEBUG，可用 JVM 参数 `-Dorg.slf4j.simpleLogger.log.org.lionsoul.ip2region.merge.MergeTest=debug` 开启。

`MakerApp` 和 `Maker` 沿用 `xdb.Log`，不读取 SLF4J 配置；使用 `--log-level=info` 或 `--log-level=debug` 同时控制入口与生成器。日志包含源文件大小、生成阶段、候选文件原子发布和最终文件大小与耗时。出现“xdb 已发布”才表示本次发布成功；失败时命令行输出参数、原因及完整堆栈，以非零状态退出。

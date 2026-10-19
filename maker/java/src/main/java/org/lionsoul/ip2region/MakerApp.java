// Copyright 2022 The Ip2Region Authors. All rights reserved.
// Use of this source code is governed by a Apache2.0-style
// license that can be found in the LICENSE file.
//
// @Author Lion <chenxin619315@gmail.com>
// @Date   2022/07/12

package org.lionsoul.ip2region;

import org.lionsoul.ip2region.xdb.IndexPolicy;
import org.lionsoul.ip2region.xdb.Log;
import org.lionsoul.ip2region.xdb.Maker;
import org.lionsoul.ip2region.xdb.Version;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 源码级双栈制库入口；候选文件全部生成成功后才替换正式库。 */
public class MakerApp {

    /** 制库进度与参数日志。 */
    public final static Log log = Log.getLogger(MakerApp.class);
    /** 字段索引或区间表达式，只允许非负整数。 */
    public final static Pattern p = Pattern.compile("^(\\d+(-\\d+)?)$");

    /**
     * 输出制库用法。
     * @param args 保留入口参数，当前不读取
     */
    public static void printHelp(String[] args) {
        System.out.println("ip2region xdb maker");
        System.out.println("java -jar ip2region-maker-{version}.jar [command options]");
        System.out.println("options:");
        System.out.println(" --src string           source ip text file path");
        System.out.println(" --dst string           destination binary xdb file path");
        System.out.println(" --version string       IP version, options: ipv4/ipv6, specify this flag so you don't get confused");
        System.out.println(" --field-list string    field index list imploded with ',' eg: 0,1,2,3-6,7");
        System.out.println(" --log-level string     set the log level, options: debug/info/warn/error");
    }

    /**
     * 解析字段选择；非法表达式抛错，禁止失败后继续制库。
     * @param fieldList 字段列表，空串表示保留全部
     * @return 排序后的字段索引
     */
    private static int[] getFieldList(String fieldList) {
        final ArrayList<Integer> list = new ArrayList<Integer>();
        final Map<String, String> map = new HashMap<String, String>();
        if (!fieldList.isEmpty()) {
            String[] fList =  fieldList.split(",");
            for (String f : fList) {
                final String s = f.trim();
                if (s.isEmpty()) {
                    log.errorf("undefined option `%s`", f);
                    throw new IllegalArgumentException("invalid field-list: " + fieldList);
                }

                final Matcher m = p.matcher(s);
                if (!m.matches()) {
                    log.errorf("field `%s` is not a number", f);
                    throw new IllegalArgumentException("invalid field-list: " + fieldList);
                }

                final String ms = m.group(1);
                if (ms.indexOf('-') == -1) {
                    if (map.containsKey(s)) {
                        log.errorf("duplicate field index `%s`", s);
                        throw new IllegalArgumentException("invalid field-list: " + fieldList);
                    }

                    map.put(s, s);
                    final int idx = Integer.parseInt(s);
                    if (idx < 0) {
                        log.errorf("field index `%s` is negative", s);
                        throw new IllegalArgumentException("invalid field-list: " + fieldList);
                    }

                    list.add(idx);
                    continue;
                }

                // index range parse
                final String[] ra = ms.split("-");
                if (ra.length != 2) {
                    log.errorf("field `%s` is not a valid range", ms);
                    throw new IllegalArgumentException("invalid field-list: " + fieldList);
                }

                final int start = Integer.parseInt(ra[0]);
                final int end = Integer.parseInt(ra[1]);
                if (start > end) {
                    log.errorf("index range start(%d) should <= end(%d)", start, end);
                    throw new IllegalArgumentException("invalid field-list: " + fieldList);
                }

                for (int i = start; i <= end; i++) {
                    final String _s = String.valueOf(i);
                    if (map.containsKey(_s)) {
                        log.errorf("duplicate field index `%s`", _s);
                        throw new IllegalArgumentException("invalid field-list: " + fieldList);
                    }

                    map.put(_s, _s);
                    list.add(i);
                }
            }
        }

        // let's keep it a complex way so the old JDK could run these pieces.
        final int[] fields = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            fields[i] = list.get(i);
        }

        // sort the fields to make sure the fields follow the original index order
        Arrays.sort(fields);
        return fields;
    }

    /**
     * 在独立候选文件制库，成功后原子发布；失败保留输入和已有输出。
     * @param args src、dst、version 及可选字段/日志参数
     * @throws Exception 参数非法、源数据错误或文件生成失败
     */
    public static void genDb(String[] args) throws Exception {
        String srcFile = "", dstFile = "", ipVersion = "";
        String fieldList = "", logLevel = "info";
        int indexPolicy = IndexPolicy.Vector;
        for (final String r : args) {
            if (r.length() < 5 || r.indexOf("--") != 0) {
                throw new IllegalArgumentException("invalid maker argument: " + r);
            }

            int sIdx = r.indexOf('=');
            if (sIdx < 0) {
                throw new IllegalArgumentException("missing = for maker argument: " + r);
            }

            String key = r.substring(2, sIdx);
            String val = r.substring(sIdx + 1);
            // System.out.printf("key=%s, val=%s\n", key, val);
            if ("src".equals(key)) {
                srcFile = val;
            } else if ("dst".equals(key)) {
                dstFile = val;
            } else if ("version".equals(key)) {
                ipVersion = val;
            } else if ("field-list".equals(key)) {
                fieldList = val;
            } else if ("log-level".equals(key)) {
                logLevel = val;
            } else {
                throw new IllegalArgumentException("undefined maker option: " + r);
            }
        }

        if (srcFile.isEmpty() || dstFile.isEmpty()) {
            throw new IllegalArgumentException("maker requires --src, --dst and --version");
        }

        // IP version
        final Version version = Version.fromName(ipVersion);

        final int[] fields = getFieldList(fieldList);

        Path source = Paths.get(srcFile).toRealPath();
        Path destination = Paths.get(dstFile).toAbsolutePath().normalize();
        if (source.equals(destination) || (Files.exists(destination) && Files.isSameFile(source, destination))) {
            throw new IllegalArgumentException("maker source and destination must differ: " + source);
        }
        long tStart = System.currentTimeMillis();
        log.infof("Generating xdb with src=%s, dst=%s, logLevel=%s", source, destination, logLevel);
        MakerApp.log.setLevel(logLevel);
        try (Maker maker = new Maker(version, indexPolicy, source.toString(), destination.toString(), fields)) {
            maker.init();
            maker.start();
            maker.end();
        }
        log.infof("Done, elapsed: %d s", (System.currentTimeMillis() - tStart) / 1000);
    }

    /**
     * 命令行制库；无参数仅显示帮助，执行失败打印参数、原因和完整堆栈并向 JVM 传播。
     * @param args 制库命令参数
     * @throws Exception 制库失败，命令行以非零退出
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printHelp(args);
            return;
        }
        try {
            genDb(args);
        } catch (Exception e) {
            System.err.println("IPv4/IPv6 maker failed, args=" + Arrays.toString(args)
                    + ", type=" + e.getClass().getName() + ", message=" + e.getMessage());
            e.printStackTrace(System.err);
            throw e;
        }
    }
}

package org.lionsoul.ip2region.merge;

import java.util.ArrayList;
import java.util.List;

/** IPv4 补丁的单行 CSV 编解码；逗号及双引号可逆转义，不接受换行及源库分隔符。 */
final class Ipv4PatchCsv {
    /** 静态编解码入口，无实例状态。 */
    private Ipv4PatchCsv() {
    }

    /**
     * 编码一条补丁；含逗号或引号的字段加引号，内部引号写成两个引号。
     * @param fields 补丁各列，不能为空元素
     * @return 不含换行符的 CSV 记录
     * @throws IllegalArgumentException 字段无法表示为单行源库数据
     */
    static String formatIpv4PatchCsv(String[] fields) {
        List<String> encoded = new ArrayList<>(fields.length);
        for (String field : fields) {
            validateIpv4PatchField(field);
            encoded.add(field.contains(",") || field.contains("\"")
                    ? "\"" + field.replace("\"", "\"\"") + "\"" : field);
        }
        return String.join(",", encoded);
    }

    /**
     * 读取历史无引号记录及带引号的单行记录，严格拒绝未闭合或位置错误的引号。
     * @param line 一条物理行，不含换行符
     * @return 保留尾部空列及原字段内容的数组
     * @throws IllegalArgumentException CSV 引号或字段内容非法
     */
    static String[] parseIpv4PatchCsv(String line) {
        List<String> fields = new ArrayList<>(8);
        int position = 0;
        while (true) {
            StringBuilder field = new StringBuilder();
            if (position < line.length() && line.charAt(position) == '"') {
                position++;
                boolean closed = false;
                while (position < line.length()) {
                    char character = line.charAt(position++);
                    if (character != '"') {
                        field.append(character);
                    } else if (position < line.length() && line.charAt(position) == '"') {
                        field.append('"');
                        position++;
                    } else {
                        closed = true;
                        break;
                    }
                }
                if (!closed || (position < line.length() && line.charAt(position) != ',')) {
                    throw new IllegalArgumentException("invalid quoted IPv4 patch: " + line);
                }
            } else {
                while (position < line.length() && line.charAt(position) != ',') {
                    char character = line.charAt(position++);
                    if (character == '"') {
                        throw new IllegalArgumentException("invalid quote in IPv4 patch: " + line);
                    }
                    field.append(character);
                }
            }
            validateIpv4PatchField(field.toString());
            fields.add(field.toString());
            if (position == line.length()) {
                return fields.toArray(new String[0]);
            }
            position++;
        }
    }

    /**
     * 限制物理行边界和七列源库边界，避免合并后产生额外记录或额外列。
     * @param field 已解码字段
     * @throws IllegalArgumentException 含换行或竖线
     */
    private static void validateIpv4PatchField(String field) {
        if (field.contains("\n") || field.contains("\r") || field.contains("|")) {
            throw new IllegalArgumentException("invalid IPv4 patch field: " + field);
        }
    }
}

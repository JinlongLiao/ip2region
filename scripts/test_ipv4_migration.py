"""验证补码冲突与无效数据拒绝，所有文件均位于临时目录。"""
import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("migration", Path(__file__).with_name("迁移IPv4数据.py"))
migration = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migration)


class Ipv4MigrationTest(unittest.TestCase):
    """隔离验证地区代码补齐策略，不读取或修改真实数据。"""

    def test_country_code_rules(self):
        """未收录的自有国家标签，仍要求上游全部覆盖段一致且代码唯一。"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            legacy, upstream, output = (root / name for name in ("old", "reference", "new"))
            legacy.write_text("0.0.0.0|255.255.255.255|自有国家|0|甲省|乙市|自有ISP\n", encoding="utf-8")
            for country, codes, expected in [("自有国家", ("CN", "CN"), "CN"),
                                             ("日本", ("JP", "JP"), "0"),
                                             ("自有国家", ("CN", "TW"), "0"),
                                             ("自有国家", ("CN", "0"), "0")]:
                upstream.write_text(f"0.0.0.0|127.255.255.255|{country}|省|市|ISP|{codes[0]}\n"
                                    f"128.0.0.0|255.255.255.255|{country}|省|市|ISP|{codes[1]}\n", encoding="utf-8")
                migration.migrate_ipv4_source(legacy, upstream, output)
                self.assertEqual(f"0.0.0.0|255.255.255.255|自有国家|甲省|乙市|自有ISP|{expected}\n",
                                 output.read_text(encoding="utf-8"))

    def test_official_country_names_and_aliases(self):
        """名称代码优先于上游区间；清理国家名空白，歧义标签保持未知。"""
        from ipv4_country_codes import resolve_country_code
        for name, code in {"中国": "CN", "中国 ": "CN", "日本": "JP", "美国": "US",
                           "印尼": "ID", "孟加拉": "BD", "波黑": "BA", "香港": "HK",
                           "台湾": "TW", "雅加达": "ID", "欧洲": "0", "圣马丁": "0", "科索沃": "XK"}.items():
            self.assertEqual(code, resolve_country_code(name))

    def test_complete_latest_source_preserves_six_fields(self):
        """补齐代码并清理国家名空白；省市 ISP 及区间原样保留。"""
        spec = importlib.util.spec_from_file_location("complete", Path(__file__).with_name("补齐IPv4国家代码.py"))
        complete = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(complete)
        with tempfile.TemporaryDirectory() as directory:
            source, destination = (Path(directory) / name for name in ("source", "output"))
            source.write_text("0.0.0.0|127.255.255.255|中国 |自有省|自有市|自有ISP|JP\n"
                              "128.0.0.0|255.255.255.255|日本|原省|原市|原ISP|0\n", encoding="utf-8")
            complete.complete_ipv4_country_codes(source, destination)
            self.assertEqual("0.0.0.0|127.255.255.255|中国|自有省|自有市|自有ISP|CN\n"
                             "128.0.0.0|255.255.255.255|日本|原省|原市|原ISP|JP\n",
                             destination.read_text(encoding="utf-8"))
            with self.assertRaises(ValueError):
                complete.complete_ipv4_country_codes(source, source)

    def test_china_regions_rewritten_as_china_province(self):
        """港澳台国家列改写为 中国|地区 并补 CN，与 Java IpAreaNormalizer 一致；城市与 ISP 保留。"""
        spec = importlib.util.spec_from_file_location("complete", Path(__file__).with_name("补齐IPv4国家代码.py"))
        complete = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(complete)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, destination, legacy = (root / name for name in ("source", "output", "legacy"))
            source.write_text("0.0.0.0|99.255.255.255|香港|葵青区|葵涌|0|HK\n"
                              "100.0.0.0|199.255.255.255|澳门|澳门|澳门|ISP|MO\n"
                              "200.0.0.0|255.255.255.255|台湾|台湾|台北市|0|TW\n", encoding="utf-8")
            complete.complete_ipv4_country_codes(source, destination)
            self.assertEqual("0.0.0.0|99.255.255.255|中国|香港|葵涌|0|CN\n"
                             "100.0.0.0|199.255.255.255|中国|澳门|澳门|ISP|CN\n"
                             "200.0.0.0|255.255.255.255|中国|台湾|台北市|0|CN\n",
                             destination.read_text(encoding="utf-8"))
            legacy.write_text("0.0.0.0|255.255.255.255|香港|0|葵青区|葵涌|0\n", encoding="utf-8")
            migration.migrate_ipv4_source(legacy, source, destination)
            self.assertEqual("0.0.0.0|255.255.255.255|中国|香港|葵涌|0|CN\n",
                             destination.read_text(encoding="utf-8"))

    def test_only_verified_country_name_corrections(self):
        """只纠正已确认的国家误填；不能用当前上游归属覆盖跨国区域和歧义旧标签。"""
        from ipv4_country_codes import normalize_country_name
        self.assertEqual("印度尼西亚", normalize_country_name("雅加达"))
        self.assertEqual("中国", normalize_country_name("中国 "))
        for name in ["欧洲", "亚太地区", "圣马丁", "安的列斯", "阿森松岛"]:
            self.assertEqual(name, normalize_country_name(name))

    def test_reject_invalid_source_and_overwriting_input(self):
        """断档、重叠、倒置、列数错误都不得生成输出；不得覆盖原件。"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            legacy, upstream, output = (root / name for name in ("old", "reference", "new"))
            upstream.write_text("0.0.0.0|255.255.255.255|中国|甲|乙|ISP|CN\n", encoding="utf-8")
            for invalid in ["1.0.0.0|255.255.255.255|中国|0|甲|乙|ISP", "invalid",
                            "0.0.0.0|1.0.0.0|中国|0|甲|乙|ISP\n1.0.0.0|255.255.255.255|中国|0|甲|乙|ISP",
                            "1.0.0.1|1.0.0.0|中国|0|甲|乙|ISP"]:
                legacy.write_text(invalid, encoding="utf-8")
                with self.assertRaises(ValueError):
                    migration.migrate_ipv4_source(legacy, upstream, output)
                self.assertFalse(output.exists())
            with self.assertRaises(ValueError):
                migration.migrate_ipv4_source(legacy, upstream, legacy)


if __name__ == "__main__":
    unittest.main()

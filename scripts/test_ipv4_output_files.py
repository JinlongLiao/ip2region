"""临时文件回归：链接别名与真实磁盘写入失败不能损坏输入和已有输出。"""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from ipv4_output_files import write_ipv4_output_atomically


def load_script(name):
    """加载中文入口用于隔离回归。"""
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class Ipv4OutputFilesTest(unittest.TestCase):
    """不读取正式数据，所有受限制写入只发生在子进程临时目录。"""

    def test_reject_all_input_hardlinks(self):
        """两个入口都必须在写入前拒绝指向任意输入的硬链接。"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old, reference, output = (root / name for name in ("old", "reference", "output"))
            old.write_text("0.0.0.0|255.255.255.255|中国|0|省|市|ISP\n")
            reference.write_text("0.0.0.0|255.255.255.255|中国|省|市|ISP|CN\n")
            migration = load_script("迁移IPv4数据")
            complete = load_script("补齐IPv4国家代码")
            for source in (old, reference):
                os.link(source, output)
                original = source.read_bytes()
                with self.assertRaises(ValueError):
                    migration.migrate_ipv4_source(old, reference, output)
                self.assertEqual(original, source.read_bytes())
                output.unlink()
            os.link(reference, output)
            with self.assertRaises(ValueError):
                complete.complete_ipv4_country_codes(reference, output)

    @unittest.skipUnless(sys.platform != "win32", "需要 POSIX 文件大小限制")
    def test_real_partial_write_preserves_existing_output(self):
        """子进程文件限制让 write/flush 实际失败，原输出和临时目录保持原状。"""
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            output.write_text("previous output")
            script = '''import resource, signal, sys
from pathlib import Path
from ipv4_output_files import write_ipv4_output_atomically
signal.signal(signal.SIGXFSZ, signal.SIG_IGN)
resource.setrlimit(resource.RLIMIT_FSIZE, (20, 20))
write_ipv4_output_atomically(Path(sys.argv[1]), "x" * 10000)
'''
            result = subprocess.run([sys.executable, "-c", script, str(output)],
                                    cwd=Path(__file__).parent, capture_output=True)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual("previous output", output.read_text())
            self.assertEqual([output], list(Path(directory).iterdir()))

    def test_shared_region_labels_cannot_inherit_reference_code(self):
        """迁移时上游给区域写错 CN 仍必须归零，不能进入未知国家的交集补码。"""
        from ipv4_country_codes import COUNTRY_CODE_DATA, resolve_country_code
        migration = load_script("迁移IPv4数据")
        with tempfile.TemporaryDirectory() as directory:
            old, reference, output = (Path(directory) / name for name in ("old", "reference", "output"))
            for label in COUNTRY_CODE_DATA["强制零地区代码标签"]:
                self.assertEqual("0", resolve_country_code(label))
                old.write_text(f"0.0.0.0|255.255.255.255|{label}|0|省|市|ISP\n")
                reference.write_text(f"0.0.0.0|255.255.255.255|{label}|省|市|ISP|CN\n")
                migration.migrate_ipv4_source(old, reference, output)
                self.assertTrue(output.read_text().endswith("|0\n"))

    def test_successfully_replace_output(self):
        """独立输出正常替换，已有目标硬链接的其他名称不被原地改写。"""
        with tempfile.TemporaryDirectory() as directory:
            output, alias = (Path(directory) / name for name in ("output", "alias"))
            output.write_text("old")
            os.link(output, alias)
            write_ipv4_output_atomically(output, "新内容\n")
            self.assertEqual("新内容\n", output.read_text())
            self.assertEqual("old", alias.read_text())


if __name__ == "__main__":
    unittest.main()

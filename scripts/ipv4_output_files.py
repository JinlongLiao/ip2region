"""IPv4 维护脚本的输入保护与原子发布；临时文件与目标位于同一文件系统。"""
import os
import tempfile
from pathlib import Path


def validate_independent_output(destination, *sources):
    """拒绝相同路径、符号链接及硬链接别名，失败时不写入任何文件。"""
    for source in sources:
        if destination.resolve() == source.resolve() or (
            destination.exists() and source.exists() and destination.samefile(source)
        ):
            raise ValueError("输出不得覆盖输入原件")


def write_ipv4_output_atomically(destination, text, *sources):
    """完整写入并同步临时文件后替换目标；写入或替换失败保留原输出并清理临时文件。

    发布前再次核对输入别名。此本地维护工具不支持其他进程同时替换输入或输出路径。
    """
    validate_independent_output(destination, *sources)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", newline="\n",
                                         dir=destination.parent, prefix=".ipv4-", delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(text)
            stream.flush()
            os.fsync(stream.fileno())
        validate_independent_output(destination, *sources)
        os.replace(temporary, destination)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)

import re
from pathlib import Path

import pymupdf

from .config import PDF_STORAGE_DIR, MAX_PAGES

MAX_FILE_SIZE = 30 * 1024 * 1024
MAX_DOCUMENTS = 10

STORAGE_NAME_PATTERN = re.compile(
    r"[0-9a-fA-F]{8}-"
    r"[0-9a-fA-F]{4}-"
    r"[0-9a-fA-F]{4}-"
    r"[0-9a-fA-F]{4}-"
    r"[0-9a-fA-F]{12}\.pdf"
)


def resolve_pdf(storage_name: str) -> Path:
    """只允许读取合法的内部PDF存储文件。"""
    if (
        not isinstance(storage_name, str)
        or not STORAGE_NAME_PATTERN.fullmatch(storage_name)
    ):
        raise ValueError("非法PDF存储文件名")

    root = PDF_STORAGE_DIR.resolve()
    path = (root / storage_name).resolve()

    if not path.is_relative_to(root):
        raise ValueError("非法PDF文件路径")

    if not path.is_file():
        raise FileNotFoundError("原始PDF文件不存在")

    return path


def merge_pdf_files(storage_names: list[str]) -> bytes:
    """合并多个PDF，返回合并后的PDF二进制数据。

    Python只负责生成PDF数据，最终文件由Java保存。
    """
    if not storage_names or len(storage_names) < 2:
        raise ValueError("至少需要2份PDF进行合并")

    if len(storage_names) > MAX_DOCUMENTS:
        raise ValueError(f"最多只能合并{MAX_DOCUMENTS}份PDF")

    # 重复文件校验
    if len(set(storage_names)) != len(storage_names):
        raise ValueError("不能重复选择同一份PDF")

    paths = [resolve_pdf(name) for name in storage_names]

    total_size = sum(path.stat().st_size for path in paths)
    if total_size > MAX_FILE_SIZE:
        raise ValueError("原始PDF总大小不能超过30MB")

    total_pages = 0
    merged = pymupdf.open()

    try:
        for path in paths:
            with pymupdf.open(path) as doc:
                # 加密PDF判断
                if doc.needs_pass:
                    raise ValueError("存在加密的PDF，无法合并")

                total_pages += doc.page_count
                if total_pages > MAX_PAGES:
                    raise ValueError(
                        f"合并后的PDF页数不能超过{MAX_PAGES}页"
                    )
                merged.insert_pdf(doc)

        if merged.page_count == 0:
            raise ValueError("合并结果为空")

        data = merged.tobytes()

        # 合并结果大小校验
        if not data or len(data) > MAX_FILE_SIZE:
            raise ValueError("合并结果为空或超过30MB")

        return data

    finally:
        merged.close()

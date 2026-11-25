from pathlib import Path

import pymupdf

from .config import PDF_STORAGE_DIR, MAX_PAGES

MAX_FILE_SIZE = 30 * 1024 * 1024
MAX_DOCUMENTS = 10


def resolve_pdf(storage_name: str) -> Path:
    """解析并校验内部PDF存储路径。

    只允许访问PDF_STORAGE_DIR中的文件。
    """
    if (
        not storage_name
        or "/" in storage_name
        or "\\" in storage_name
        or not storage_name.lower().endswith(".pdf")
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

    paths = [resolve_pdf(name) for name in storage_names]

    total_size = sum(path.stat().st_size for path in paths)
    if total_size > MAX_FILE_SIZE:
        raise ValueError("原始PDF总大小不能超过30MB")

    total_pages = 0
    merged = pymupdf.open()

    try:
        for path in paths:
            with pymupdf.open(path) as doc:
                total_pages += doc.page_count
                if total_pages > MAX_PAGES:
                    raise ValueError(
                        f"合并后的PDF页数不能超过{MAX_PAGES}页"
                    )
                merged.insert_pdf(doc)

        if merged.page_count == 0:
            raise ValueError("合并结果为空")

        return merged.tobytes()

    finally:
        merged.close()

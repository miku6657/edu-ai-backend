import re
from pathlib import Path

import pymupdf

from .config import MAX_PAGES, MAX_EXTRACTED_CHARS


def split_text(
    text: str,
    chunk_size: int = 800,
    overlap: int = 120,
) -> list[str]:
    if chunk_size <= 0 or overlap < 0:
        raise ValueError("切片参数不合法")
    if overlap >= chunk_size:
        raise ValueError("重叠长度不能大于等于切片长度")

    text = re.sub(r"\s+", " ", text).strip()
    if not text:
        return []

    chunks = []
    start = 0
    step = chunk_size - overlap

    while start < len(text):
        end = start + chunk_size
        chunks.append(text[start:end])
        if end >= len(text):
            break
        start += step

    return chunks


def parse_pdf(path: str | Path) -> list[dict]:
    """解析 PDF，返回每段文本及其页码。

    返回结构：
        [
            {"page": 1, "chunk_index": 0, "text": "..."},
            {"page": 1, "chunk_index": 1, "text": "..."},
            ...
        ]
    """
    path = Path(path)
    if not path.is_file():
        raise FileNotFoundError(f"PDF 文件不存在: {path}")

    results = []
    total_chars = 0

    with pymupdf.open(path) as doc:
        if doc.page_count > MAX_PAGES:
            raise ValueError(f"PDF 页数超过限制 {MAX_PAGES}")

        for page_no, page in enumerate(doc, start=1):
            text = page.get_text("text")
            total_chars += len(text)

            if total_chars > MAX_EXTRACTED_CHARS:
                raise ValueError("PDF 文本内容超过提取上限")

            for idx, chunk in enumerate(split_text(text)):
                results.append({
                    "page": page_no,
                    "chunk_index": idx,
                    "text": chunk,
                })

    return results

import re
from bisect import bisect_right
from pathlib import Path

import pymupdf

from .config import MAX_PAGES, MAX_EXTRACTED_CHARS

LEGACY_CHUNKING_VERSION = 1
PARAGRAPH_CHUNKING_VERSION = 2
SECTION_CHUNKING_VERSION = 3
# 一个章节最多6000字符
MAX_SECTION_CHARS = 6000

DEFAULT_CHUNK_SIZE = 800
DEFAULT_OVERLAP = 120


def validate_chunk_parameters(
    chunk_size: int,
    overlap: int,
) -> None:
    if chunk_size <= 0 or overlap < 0:
        raise ValueError("切片参数不合法")
    if overlap >= chunk_size:
        raise ValueError("重叠长度不能大于等于切片长度")


def split_text(
    text: str,
    chunk_size: int = 800,
    overlap: int = 120,
) -> list[str]:
    """V1旧版切片算法：固定字符窗口，压缩空白。"""
    validate_chunk_parameters(chunk_size, overlap)

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


def _split_long_paragraph(
    text: str,
    chunk_size: int,
    overlap: int,
) -> list[str]:
    """对超长段落执行滑动窗口切分，保留换行。"""
    text = text.strip()
    if not text:
        return []

    chunks = []
    start = 0
    step = chunk_size - overlap

    while start < len(text):
        end = start + chunk_size
        chunks.append(text[start:end].strip())
        if end >= len(text):
            break
        start += step

    return chunks


def _build_section_map(doc) -> dict[int, str]:
    """根据PDF目录书签构建页码到章节标题的映射。"""
    section_map = {}

    toc = doc.get_toc()
    if not toc:
        return section_map

    for _level, title, page in toc:
        if title is None:
            continue
        title = title.strip()
        if not title:
            continue
        section_map[page] = title

    return section_map


def _section_for_page(
    section_pages: list[int],
    section_map: dict[int, str],
    page_no: int,
) -> str | None:
    """根据页码查找最接近的章节标题。"""
    if not section_pages:
        return None

    idx = bisect_right(section_pages, page_no)
    if idx > 0:
        key = section_pages[idx - 1]
        return section_map.get(key)

    return None


def _parse_page_paragraphs(
    page,
    page_no: int,
    section_title: str | None,
) -> list[dict]:
    """V2段落感知切片：优先按PyMuPDF文本块组织内容。"""
    blocks = page.get_text("blocks")

    # blocks 结构：(x0, y0, x1, y1, text, block_no, block_type)
    # block_type 0 表示文本块。
    text_blocks = [
        block[4].strip()
        for block in blocks
        if len(block) >= 7
        and block[6] == 0
        and isinstance(block[4], str)
        and block[4].strip()
    ]

    if not text_blocks:
        text = page.get_text("text").strip()
        text_blocks = [text] if text else []

    chunks = []
    chunk_index = 0

    for block_text in text_blocks:
        paragraphs = [
            p.strip()
            for p in re.split(r"\n\s*\n", block_text)
            if p.strip()
        ]

        if not paragraphs:
            paragraphs = [block_text.strip()]

        for paragraph in paragraphs:
            if len(paragraph) <= DEFAULT_CHUNK_SIZE:
                chunks.append({
                    "page": page_no,
                    "chunk_index": chunk_index,
                    "section_title": section_title,
                    "text": paragraph,
                })
                chunk_index += 1
            else:
                for sub in _split_long_paragraph(
                    paragraph,
                    DEFAULT_CHUNK_SIZE,
                    DEFAULT_OVERLAP,
                ):
                    chunks.append({
                        "page": page_no,
                        "chunk_index": chunk_index,
                        "section_title": section_title,
                        "text": sub,
                    })
                    chunk_index += 1

    return chunks


def _extract_heading(line: str) -> str | None:
    """识别教材章节标题。"""
    line = re.sub(r"\s+", " ", line).strip()
    if not line or len(line) > 120:
        return None

    # 1.1、1.1.2、1.1.2.3 等编号
    match = re.match(
        r"^(\d{1,3}(?:\.\d{1,3}){1,5})\s*[、.．]?\s*"
        r"([一-鿿A-Za-z（(【《].{0,100})$",
        line,
    )
    if match:
        return f"{match.group(1)} {match.group(2).strip()}"

    # 第一章、第二节等
    match = re.match(
        r"^(第[一二三四五六七八九十百零〇\d]+[章节篇]\s*"
        r"[一-鿿A-Za-z0-9（(【《].{0,100})$",
        line,
    )
    if match:
        return match.group(1).strip()

    return None


def _read_lines(page) -> list[str]:
    """按照PDF阅读顺序获取文本行。"""
    text = page.get_text("text", sort=True) or ""
    return [
        line.strip()
        for line in text.split("\n")
        if line.strip()
    ]


def _parse_v3(doc) -> list[dict]:
    """V3章节聚合切片：按识别出的章节标题聚合文本。

    每个切片不超过 MAX_SECTION_CHARS，超长章节拆分后保留章节标题。
    """
    chunks = []
    chunk_index = 0
    current_section = None
    current_page = None
    buffer = []
    buffer_len = 0

    def flush():
        nonlocal chunk_index, buffer_len
        text = "\n".join(buffer).strip()
        if text:
            chunks.append({
                "page": current_page,
                "chunk_index": chunk_index,
                "section_title": current_section,
                "text": text,
            })
            chunk_index += 1
        buffer.clear()
        buffer_len = 0

    for page_no, page in enumerate(doc, start=1):
        for line in _read_lines(page):
            heading = _extract_heading(line)

            if heading is not None:
                flush()
                current_section = heading
                current_page = page_no
                buffer.append(line)
                buffer_len = len(line)
                continue

            if current_page is None:
                current_page = page_no

            # 加入新行后（含换行符）是否超过上限。
            new_len = buffer_len + (1 if buffer else 0) + len(line)

            if buffer and new_len > MAX_SECTION_CHARS:
                flush()
                # 超长章节拆分后保留章节标题。
                current_page = page_no
                buffer.append(line)
                buffer_len = len(line)
            else:
                buffer.append(line)
                buffer_len = new_len

    flush()
    return chunks


def parse_pdf(
    path: str | Path,
    chunking_version: int = 1,
) -> list[dict]:
    """解析 PDF，返回每段文本及其页码、章节标题。

    返回结构：
        [
            {
                "page": 1,
                "chunk_index": 0,
                "section_title": "第三章 ..." 或 None,
                "text": "...",
            },
            ...
        ]
    """
    if chunking_version not in (
        LEGACY_CHUNKING_VERSION,
        PARAGRAPH_CHUNKING_VERSION,
        SECTION_CHUNKING_VERSION,
    ):
        raise ValueError("不支持的切片算法版本")

    path = Path(path)
    if not path.is_file():
        raise FileNotFoundError(f"PDF 文件不存在: {path}")

    results = []
    total_chars = 0

    with pymupdf.open(path) as doc:
        if doc.page_count > MAX_PAGES:
            raise ValueError(f"PDF 页数超过限制 {MAX_PAGES}")

        # V3章节聚合切片
        if chunking_version == SECTION_CHUNKING_VERSION:
            results = _parse_v3(doc)
            total_chars = sum(
                len(item["text"])
                for item in results
            )

            if total_chars > MAX_EXTRACTED_CHARS:
                raise ValueError("PDF 文本内容超过提取上限")

            return results

        section_map = _build_section_map(doc)
        section_pages = sorted(section_map.keys())

        for page_no, page in enumerate(doc, start=1):
            section_title = _section_for_page(
                section_pages,
                section_map,
                page_no,
            )

            if chunking_version == LEGACY_CHUNKING_VERSION:
                text = page.get_text("text")
                total_chars += len(text)

                if total_chars > MAX_EXTRACTED_CHARS:
                    raise ValueError("PDF 文本内容超过提取上限")

                for idx, chunk in enumerate(split_text(text)):
                    results.append({
                        "page": page_no,
                        "chunk_index": idx,
                        "section_title": section_title,
                        "text": chunk,
                    })

            else:
                chunks = _parse_page_paragraphs(
                    page,
                    page_no,
                    section_title,
                )

                for chunk in chunks:
                    total_chars += len(chunk["text"])

                    if total_chars > MAX_EXTRACTED_CHARS:
                        raise ValueError("PDF 文本内容超过提取上限")

                results.extend(chunks)

    return results

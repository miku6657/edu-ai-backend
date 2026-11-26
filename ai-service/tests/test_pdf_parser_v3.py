import unittest
from unittest.mock import MagicMock

from app.pdf_parser import (
    MAX_SECTION_CHARS,
    _extract_heading,
    _parse_v3,
)


def _page(text: str) -> MagicMock:
    page = MagicMock()
    page.get_text.return_value = text
    return page


def _doc(pages) -> MagicMock:
    doc = MagicMock()
    doc.page_count = len(pages)
    doc.__iter__.return_value = iter(pages)
    return doc


class PdfParserV3Test(unittest.TestCase):

    def test_three_numbered_sections(self):
        text = (
            "1.1 第一章\n"
            "这是第一章内容\n"
            "1.2 第二章\n"
            "这是第二章内容\n"
            "1.3 第三章\n"
            "这是第三章内容"
        )
        chunks = _parse_v3(_doc([_page(text)]))

        self.assertEqual(len(chunks), 3)
        self.assertEqual(chunks[0]["section_title"], "1.1 第一章")
        self.assertEqual(chunks[1]["section_title"], "1.2 第二章")
        self.assertEqual(chunks[2]["section_title"], "1.3 第三章")

    def test_section_across_pages(self):
        page1 = _page("1.1 第一章\n第一页内容")
        page2 = _page("第二页内容（仍属于第一章）")
        chunks = _parse_v3(_doc([page1, page2]))

        self.assertEqual(len(chunks), 1)
        self.assertEqual(chunks[0]["section_title"], "1.1 第一章")

    def test_inline_enumeration_not_heading(self):
        # 正文中的“1.效率高2.维护性强”不能误判为章节标题。
        self.assertIsNone(_extract_heading("1.效率高2.维护性强"))

    def test_no_heading_aggregates_continuous_text(self):
        text = "这是普通正文段落一\n这是普通正文段落二"
        chunks = _parse_v3(_doc([_page(text)]))

        self.assertEqual(len(chunks), 1)
        self.assertIsNone(chunks[0]["section_title"])

    def test_long_section_splits_keeping_title(self):
        line = "这是一段用于撑满章节长度的正文内容。"
        # 400 行，合计超过 6000 字符。
        text = "2.1 长章节\n" + "\n".join([line] * 400)
        chunks = _parse_v3(_doc([_page(text)]))

        self.assertGreater(len(chunks), 1)
        for chunk in chunks:
            self.assertEqual(chunk["section_title"], "2.1 长章节")
            self.assertLessEqual(len(chunk["text"]), MAX_SECTION_CHARS)


if __name__ == "__main__":
    unittest.main()

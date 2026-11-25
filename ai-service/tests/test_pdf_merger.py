import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

import pymupdf

from app import pdf_merger


class PdfMergerTest(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.storage_patch = patch.object(
            pdf_merger,
            "PDF_STORAGE_DIR",
            self.root,
        )
        self.storage_patch.start()

    def tearDown(self):
        self.storage_patch.stop()
        self.temp_dir.cleanup()

    def create_pdf(self, text: str, pages: int = 1) -> str:
        storage_name = f"{uuid.uuid4()}.pdf"
        path = self.root / storage_name

        doc = pymupdf.open()
        for _ in range(pages):
            page = doc.new_page()
            page.insert_text((72, 72), text)
        doc.save(path)
        doc.close()

        return storage_name

    def test_resolve_rejects_invalid_storage_name(self):
        with self.assertRaises(ValueError):
            pdf_merger.resolve_pdf("not-a-uuid.pdf")

    def test_resolve_rejects_path_traversal(self):
        name = f"../../{uuid.uuid4()}.pdf"
        with self.assertRaises(ValueError):
            pdf_merger.resolve_pdf(name)

    def test_resolve_returns_path_for_valid_name(self):
        name = self.create_pdf("hello")
        path = pdf_merger.resolve_pdf(name)
        self.assertEqual(path, (self.root / name).resolve())

    def test_merge_rejects_single_document(self):
        name = self.create_pdf("hello")
        with self.assertRaises(ValueError):
            pdf_merger.merge_pdf_files([name])

    def test_merge_rejects_duplicate_storage_names(self):
        name = self.create_pdf("hello")
        with self.assertRaises(ValueError):
            pdf_merger.merge_pdf_files([name, name])

    def test_merge_rejects_missing_file(self):
        missing = f"{uuid.uuid4()}.pdf"
        other = self.create_pdf("hello")
        with self.assertRaises(FileNotFoundError):
            pdf_merger.merge_pdf_files([missing, other])

    def test_merge_returns_pdf_bytes(self):
        name_a = self.create_pdf("document A")
        name_b = self.create_pdf("document B")
        data = pdf_merger.merge_pdf_files([name_a, name_b])
        self.assertIsInstance(data, bytes)
        self.assertTrue(data.startswith(b"%PDF-"))
        self.assertGreater(len(data), 0)


if __name__ == "__main__":
    unittest.main()

import importlib.util
from pathlib import Path
import struct
import unittest

spec = importlib.util.spec_from_file_location("apk_pages", Path(__file__).parents[1] / "check-apk-pages.py")
pages = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pages)


class ElfAlignmentTest(unittest.TestCase):
    def elf(self, alignment=16384, offset=0, address=0):
        data = bytearray(120)
        data[:6] = b"\x7fELF\x02\x01"
        struct.pack_into("<Q", data, 32, 64)
        struct.pack_into("<HH", data, 54, 56, 1)
        struct.pack_into("<I", data, 64, 1)
        struct.pack_into("<QQ", data, 72, offset, address)
        struct.pack_into("<Q", data, 112, alignment)
        return data

    def test_16kb_and_64kb_segments_pass(self):
        for alignment in [16384, 65536]:
            self.assertTrue(pages.load_segments(self.elf(alignment))[0]["supports16KB"])

    def test_4kb_or_incongruent_segment_is_rejected(self):
        self.assertFalse(pages.load_segments(self.elf(4096))[0]["supports16KB"])
        self.assertFalse(pages.load_segments(self.elf(offset=4096))[0]["supports16KB"])

    def test_invalid_and_empty_elf_are_rejected(self):
        with self.assertRaises(ValueError):
            pages.load_segments(b"not an ELF")
        data = self.elf()
        struct.pack_into("<H", data, 56, 0)
        with self.assertRaises(ValueError):
            pages.load_segments(data)

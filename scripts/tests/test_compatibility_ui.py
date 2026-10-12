import sys
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from compatibility_ui import Device


class UiTargetTest(unittest.TestCase):
    def setUp(self):
        self.device = Device.__new__(Device)

    def test_clipped_picker_button_does_not_target_the_whole_screen(self):
        root = ET.fromstring('''<hierarchy><node bounds="[0,0][1080,2072]" clickable="false">
            <node bounds="[0,0][0,0]" clickable="false"><node text="USE THIS FOLDER"
                bounds="[0,0][0,0]" clickable="true" /></node></node></hierarchy>''')
        self.assertIsNone(self.device.find(text="USE THIS FOLDER", root=root))

    def test_clipped_text_uses_its_clickable_button(self):
        root = ET.fromstring('''<hierarchy><node bounds="[0,0][1080,2340]" clickable="false">
            <node bounds="[100,200][500,300]" clickable="true"><node text="확인"
                bounds="[0,0][0,0]" clickable="false" /></node></node></hierarchy>''')
        self.assertEqual((300, 250), self.device.find(text="확인", root=root))

    def test_visible_target_and_missing_target_remain_distinct(self):
        root = ET.fromstring('''<hierarchy><node text="확인" bounds="[110,200][210,260]" /></hierarchy>''')
        self.assertEqual((160, 230), self.device.find(text="확인", root=root))
        self.assertIsNone(self.device.find(text="취소", root=root))


if __name__ == "__main__":
    unittest.main()

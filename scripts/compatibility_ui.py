"""UI and snapshot helpers for disposable Android compatibility devices."""
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import subprocess
import time
import xml.etree.ElementTree as ET


class Device:
    def __init__(self, adb, serial, avd, output, package="com.poyal.perilog"):
        if not re.fullmatch(r"emulator-\d+", serial) or not avd.startswith("perilog_compat_"):
            raise ValueError("A named disposable compatibility emulator is required")
        self.command = [str(adb), "-s", serial]
        self.output, self.package = Path(output), package
        if self.adb("emu", "avd", "name").splitlines()[0] != avd:
            raise ValueError("Unexpected AVD; refusing to touch data")
        if self.adb("shell", "getprop", "ro.kernel.qemu") != "1":
            raise ValueError("Only emulators are supported")
        self.api = int(self.adb("shell", "getprop", "ro.build.version.sdk"))

    def adb(self, *args, binary=False, timeout=60):
        value = subprocess.check_output(self.command + list(args), timeout=timeout)
        return value if binary else value.decode().strip()

    def tree(self):
        for _ in range(5):
            # Keep dumps outside shared storage: DocumentsUI watches it and may
            # refresh its rows while a subsequent file-selection tap is delivered.
            result = self.adb("shell", "uiautomator", "dump", "--compressed", "/data/local/tmp/perilog-compat-ui.xml")
            if "dumped to" in result:
                return ET.fromstring(self.adb("shell", "cat", "/data/local/tmp/perilog-compat-ui.xml"))
            time.sleep(.4)
        raise AssertionError("UI hierarchy unavailable")

    def find(self, text=None, desc=None, resource=None, kind=None, root=None):
        root = self.tree() if root is None else root
        parents = {child: parent for parent in root.iter() for child in parent}
        for node in root.iter("node"):
            if (text is None or node.get("text") == text) and (desc is None or node.get("content-desc") == desc) and (resource is None or node.get("resource-id", "").endswith(resource)) and (kind is None or node.get("class") == kind):
                candidate = node
                while candidate is not None:
                    bounds = list(map(int, re.findall(r"-?\d+", candidate.get("bounds", ""))))
                    if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1] and (candidate is node or candidate.get("clickable") == "true"):
                        return (bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2
                    candidate = parents.get(candidate)
        return None

    def tap(self, text=None, desc=None, resource=None, kind=None, scroll=False):
        for _ in range(12 if scroll else 5):
            root = self.tree()
            point = self.find(text, desc, resource, kind, root)
            if text in ("USE THIS FOLDER", "SAVE") and point is None and any(
                n.get("text") == text and n.get("enabled") == "true" and
                n.get("resource-id") == "android:id/button1" and "documentsui" in n.get("package", "")
                for n in root.iter("node")
            ):
                # Older uiautomator builds clip the bottom picker button to [0,0].
                # This fallback is limited to our portrait, three-button AVD;
                # retain the actual screen and require the next dialog/result.
                assert self.adb("shell", "settings", "get", "secure", "navigation_mode") == "0"
                width, height = map(int, re.findall(r"(\d+)x(\d+)", self.adb("shell", "wm", "size"))[-1])
                density = int(re.findall(r":\s*(\d+)", self.adb("shell", "wm", "density"))[-1]) / 160
                assert height > width
                self.capture("picker-bottom-" + text.lower().replace(" ", "-"))
                x = width // 2 if text == "USE THIS FOLDER" else width - round(48 * density)
                self.adb("shell", "input", "tap", str(x), str(height - round(72 * density)))
                time.sleep(.3)
                return
            if point:
                if text and self.api <= 30 and any(
                    n.get("resource-id", "").endswith("documentsui:id/item_root") and
                    any(child.get("text") == text for child in n.iter("node")) for n in root.iter("node")
                ):
                    self.select_document(text)
                    return
                self.adb("shell", "input", "tap", *map(str, point))
                time.sleep(.3)
                return
            if scroll:
                areas = [list(map(int, re.findall(r"\d+", node.get("bounds", "")))) for node in root.iter("node") if node.get("scrollable") == "true"]
                if areas:
                    x1, y1, x2, y2 = max(areas, key=lambda b: (b[2] - b[0]) * (b[3] - b[1]))
                    self.adb("shell", "input", "swipe", str((x1+x2)//2), str(int(y1+(y2-y1)*.8)), str((x1+x2)//2), str(int(y1+(y2-y1)*.2)), "250")
            time.sleep(.4)
        self.capture("missing-control")
        raise AssertionError("Missing control: " + str(text or desc or resource or kind))

    def select_document(self, text):
        # The API 29/30 DocumentsUI image ignores injected row taps. Use its
        # standard keyboard navigation and verify the focused filename before
        # opening it, never assume an index or accept a different document.
        for _ in range(50):
            root = self.tree()
            for node in root.iter("node"):
                if node.get("focused") == "true" and (node.get("resource-id", "").endswith("documentsui:id/item_root") or node.get("text") == text):
                    if any(child.get("text") == text for child in node.iter("node")):
                        self.adb("shell", "input", "keyevent", "KEYCODE_ENTER")
                        time.sleep(.5)
                        return
            self.adb("shell", "input", "keyevent", "KEYCODE_TAB")
        raise AssertionError("Document keyboard focus not reached: " + text)

    def wait_text(self, text):
        for _ in range(12):
            if self.find(text=text):
                return
            time.sleep(.5)
        raise AssertionError("Text did not appear: " + text)

    def capture(self, name):
        root = self.tree()
        (self.output / (name + ".xml")).write_text(ET.tostring(root, encoding="unicode"))
        (self.output / (name + ".png")).write_bytes(self.adb("exec-out", "screencap", "-p", binary=True))
        return root

    def start(self):
        self.adb("shell", "am", "start", "-W", "-n", self.package + "/com.poyal.perilog.MainActivity")
        time.sleep(.7)

    def restart(self):
        self.adb("shell", "am", "force-stop", self.package)
        self.start()

    def back(self):
        self.adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(.3)

    def settings(self):
        self.restart()
        self.tap(desc="설정")

    def snapshot(self, name):
        self.adb("shell", "am", "force-stop", self.package)
        target = self.output / name
        target.mkdir()
        self.adb("pull", f"/data/user/0/{self.package}/databases", str(target))
        self.adb("pull", f"/data/user/0/{self.package}/files", str(target))
        with sqlite3.connect(target / "databases/perilog.db") as connection:
            connection.row_factory = sqlite3.Row
            tables = [row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table','android_metadata')")]
            data = {"schema": connection.execute("PRAGMA user_version").fetchone()[0],
                    "integrity": connection.execute("PRAGMA integrity_check").fetchone()[0],
                    "tables": {table: sorted([dict(row) for row in connection.execute('SELECT * FROM "' + table.replace('"', '""') + '"')], key=lambda row: json.dumps(row, sort_keys=True)) for table in tables}}
        data["protectedFiles"] = {str(p.relative_to(target / "files")): hashlib.sha256(p.read_bytes()).hexdigest()
                                  for p in (target / "files/protection").glob("*") if p.is_file()}
        report = self.adb("shell", "dumpsys", "package", self.package)
        (target / "package.txt").write_text(report)
        data["uid"] = re.search(r"\b(?:appId|userId)=(\d+)", report)[1]
        data["firstInstallTime"] = re.search(r"\bfirstInstallTime=([^\n]+)", report)[1]
        (target / "uri-permissions.txt").write_text(self.adb("shell", "dumpsys", "activity", "permissions"))
        (target / "snapshot.json").write_text(json.dumps(data, ensure_ascii=False, indent=2))
        return data

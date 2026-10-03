"""Pull actual documentation captures from an explicitly selected disposable emulator."""
import argparse
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("serial")
parser.add_argument("--avd", required=True, help="Expected test AVD name")
args = parser.parse_args()
if not re.fullmatch(r"emulator-\d+", args.serial):
    raise SystemExit("Use a documentation emulator, never a personal device")
os.environ.setdefault("ANDROID_USER_HOME", str(ROOT / ".tools/android-user"))
sdk = Path(os.environ.get("ANDROID_HOME", ROOT / ".tools/android-sdk"))
adb = sdk / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")

def command(*parts):
    return subprocess.check_output([str(adb), "-s", args.serial, *parts], timeout=30)

if command("emu", "avd", "name").decode().splitlines()[0].strip() != args.avd:
    raise SystemExit("Unexpected AVD; no screenshots collected")
names = command("shell", "run-as", "com.poyal.perilog.debug", "ls", "files/manual-screenshots").decode().split()
output = ROOT / "docs/screenshots"
output.mkdir(exist_ok=True)
count = 0
for name in names:
    if not re.fullmatch(r"\d{2}-[a-z0-9-]+\.png", name):
        raise SystemExit(f"Unexpected capture name: {name}")
    data = command("exec-out", "run-as", "com.poyal.perilog.debug", "cat", f"files/manual-screenshots/{name}")
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise SystemExit(f"Not a PNG: {name}")
    (output / name).write_bytes(data)
    count += 1
print(f"Collected {count} actual screenshots into {output}")

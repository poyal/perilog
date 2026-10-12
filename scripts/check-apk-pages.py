#!/usr/bin/env python3
"""Check APK ZIP alignment and every packaged 64-bit ELF PT_LOAD for 16 KB devices."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def load_segments(data):
    if data[:4] != b"\x7fELF" or data[4] not in (1, 2) or data[5] not in (1, 2):
        raise ValueError("Invalid ELF header")
    endian = "<" if data[5] == 1 else ">"
    is64 = data[4] == 2
    offset = struct.unpack_from(endian + ("Q" if is64 else "I"), data, 32 if is64 else 28)[0]
    size, count = struct.unpack_from(endian + "HH", data, 54 if is64 else 42)
    if not count or size < (56 if is64 else 32):
        raise ValueError("Missing ELF program headers")
    result = []
    for index in range(count):
        pos = offset + size * index
        if struct.unpack_from(endian + "I", data, pos)[0] != 1:
            continue
        file_offset, address = struct.unpack_from(endian + ("QQ" if is64 else "II"), data, pos + (8 if is64 else 4))
        alignment = struct.unpack_from(endian + ("Q" if is64 else "I"), data, pos + (48 if is64 else 28))[0]
        result.append({"offset": file_offset, "address": address, "alignment": alignment,
                       "supports16KB": alignment >= 16384 and alignment & (alignment - 1) == 0
                       and file_offset % 16384 == address % 16384})
    if not result:
        raise ValueError("ELF has no loadable segments")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    zipalign = Path(os.environ.get("ANDROID_HOME", ROOT / ".tools/android-sdk")) / "build-tools/36.0.0/zipalign"
    check = subprocess.run([str(zipalign), "-c", "-P", "16", "-v", "4", str(args.apk)], capture_output=True, text=True)
    libraries = []
    with zipfile.ZipFile(args.apk) as archive:
        for name in sorted(archive.namelist()):
            if name.startswith("lib/") and name.endswith(".so"):
                segments = load_segments(archive.read(name))
                required = name.split("/")[1] in ("arm64-v8a", "x86_64")
                libraries.append({"name": name, "requiredFor16KB": required, "segments": segments,
                                  "supports16KB": all(s["supports16KB"] for s in segments)})
    passed = check.returncode == 0 and all(lib["supports16KB"] for lib in libraries if lib["requiredFor16KB"])
    result = {"status": "PASS" if passed else "FAIL", "apk": str(args.apk.resolve()),
              "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(), "libraries": libraries,
              "zipalignExitCode": check.returncode, "zipalign": check.stdout + check.stderr,
              "runtimeTestRequired": True}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x") as output:
        json.dump(result, output, ensure_ascii=False, indent=2)
        output.write("\n")
    print(f"{result['status']}: ZIP alignment and {sum(lib['requiredFor16KB'] for lib in libraries)} 64-bit libraries; runtime verification is separate")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Create/start/stop isolated compatibility AVDs without touching preview devices."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["start", "stop"])
    parser.add_argument("--api", type=int, choices=range(29, 38), required=True)
    parser.add_argument("--page-size", type=int, choices=[4096, 16384], default=4096)
    parser.add_argument("--port", type=int, default=5590)
    args = parser.parse_args()
    if args.port % 2 or not 5554 <= args.port <= 5682:
        parser.error("Use an even emulator port between 5554 and 5682")
    if args.page_size == 16384 and args.api < 35:
        parser.error("16 KB images require API 35+")
    sdk = ROOT / ".tools/android-sdk"
    avds = ROOT / ".tools/compat-avd"
    avds.mkdir(parents=True, exist_ok=True)
    env = {**os.environ, "JAVA_HOME": str(ROOT / ".tools/jdk"), "ANDROID_HOME": str(sdk),
           "ANDROID_USER_HOME": str(ROOT / ".tools/android-user"), "ANDROID_AVD_HOME": str(avds)}
    name = f"perilog_compat_api{args.api}" + ("_16k" if args.page_size == 16384 else "")
    serial = f"emulator-{args.port}"
    adb = [str(sdk / "platform-tools/adb"), "-s", serial]

    def connected():
        try:
            result = subprocess.run(adb + ["get-state"], capture_output=True, text=True, timeout=10)
        except subprocess.TimeoutExpired:
            return False
        return result.returncode == 0

    def verify_runtime():
        actual_api = subprocess.check_output(adb + ["shell", "getprop", "ro.build.version.sdk"], text=True, timeout=10).strip()
        actual_page = subprocess.check_output(adb + ["shell", "getconf", "PAGE_SIZE"], text=True, timeout=10).strip()
        if int(actual_api) != args.api or int(actual_page) != args.page_size:
            raise SystemExit(f"Unexpected runtime: API {actual_api}, page size {actual_page}")
        return actual_page

    if connected():
        current = subprocess.check_output(adb + ["emu", "avd", "name"], text=True, timeout=10).splitlines()[0]
        if current != name:
            raise SystemExit(f"{serial} belongs to {current}; refusing to touch it")
        if args.action == "stop":
            subprocess.run(adb + ["emu", "kill"], check=True, timeout=10)
            deadline = time.monotonic() + 30
            while connected() and time.monotonic() < deadline:
                time.sleep(.5)
            if connected():
                raise SystemExit("Emulator did not stop")
            return
        if subprocess.check_output(adb + ["shell", "getprop", "sys.boot_completed"], text=True, timeout=10).strip() != "1":
            raise SystemExit("The existing emulator is still booting; inspect its log before continuing")
        page = verify_runtime()
        print(f"Already running: {name} / {serial} / page size {page}")
        return
    if args.action == "stop":
        print(f"Already stopped: {name}")
        return
    tag = "google_apis_ps16k" if args.page_size == 16384 else "google_apis"
    version = "37.0" if args.api == 37 else str(args.api)
    image = f"system-images;android-{version};{tag};arm64-v8a"
    if not (sdk / "system-images" / ("android-" + version) / tag / "arm64-v8a/package.xml").exists():
        raise SystemExit("Install the official image with sdkmanager first: " + image)
    managers = []
    for properties in (sdk / "cmdline-tools").glob("*/source.properties"):
        revision = re.search(r"^Pkg.Revision\s*=\s*(\d+(?:\.\d+)*)\s*$", properties.read_text(), re.M)
        if revision and (properties.parent / "bin/avdmanager").exists():
            managers.append((tuple(map(int, revision[1].split("."))), properties.parent / "bin/avdmanager"))
    if not managers:
        raise SystemExit("Android SDK command-line tools are required")
    revision, manager = max(managers)
    if args.api >= 37 and revision < (20,):
        raise SystemExit("API 37's dotted version requires command-line tools 20+; install cmdline-tools;latest")
    if not (avds / (name + ".ini")).exists():
        subprocess.run([str(manager), "create", "avd", "--name", name,
                        "--package", image, "--device", "pixel_5", "--path", str(avds / (name + ".avd"))],
                       input="no\n", text=True, check=True, env=env)
    index = avds / (name + ".ini")
    metadata = index.read_text()
    if args.api >= 37 and "target=android-0\n" in metadata:
        # Tools 19 misparsed 37.0. Repair only our AVD with the matching image;
        # keep its disk and the original index, then verify the booted SDK below.
        config = (avds / (name + ".avd/config.ini")).read_text()
        expected_image = f"system-images/android-{version}/{tag}/arm64-v8a/"
        assert re.search(r"^image.sysdir.1\s*=\s*" + re.escape(expected_image) + r"\s*$", config, re.M)
        backup = index.with_suffix(".ini.before-api-repair")
        if not backup.exists():
            backup.write_text(metadata)
        index.write_text(metadata.replace("target=android-0\n", "target=android-" + version + "\n"))
    log_path = avds / (name + "-" + time.strftime("%Y%m%dT%H%M%S") + ".log")
    with log_path.open("x") as log:
        emulator = subprocess.Popen([str(sdk / "emulator/emulator"), "-avd", name, "-port", str(args.port),
                                     "-no-window", "-no-snapshot", "-no-audio", "-no-boot-anim",
                                     "-gpu", "swiftshader_indirect", "-cores", "4", "-memory", "4096" if args.api >= 37 else "2048",
                                     "-camera-back", "none", "-camera-front", "none"],
                                    env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        if emulator.poll() is not None:
            raise SystemExit("Emulator exited; see " + str(log_path))
        if connected():
            try:
                boot = subprocess.check_output(adb + ["shell", "getprop", "sys.boot_completed"], text=True, timeout=10).strip()
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
                time.sleep(2)
                continue
            if boot == "1":
                actual_page = verify_runtime()
                subprocess.run(adb + ["shell", "input", "keyevent", "KEYCODE_WAKEUP"], check=True)
                subprocess.run(adb + ["shell", "wm", "dismiss-keyguard"], check=True)
                print(f"Ready: {name} / {serial} / page size {actual_page}; {log_path}")
                return
        time.sleep(2)
    raise SystemExit("Boot timed out; see " + str(log_path))


if __name__ == "__main__":
    main()

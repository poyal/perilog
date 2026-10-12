#!/usr/bin/env python3
"""Resize a real freeform app task while retaining an unsaved synthetic form."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import time
from compatibility_ui import Device

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.poyal.perilog.debug"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--avd", required=True)
    parser.add_argument("--apk", type=Path, required=True, help="Fixed debug APK expected on the device")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    sdk = Path(os.environ.get("ANDROID_HOME", ROOT / ".tools/android-sdk"))
    device = Device(sdk / "platform-tools/adb", args.serial, args.avd, args.output, PACKAGE)
    args.output.mkdir(parents=True, exist_ok=False)
    settings = ["enable_freeform_support", "force_resizable_activities"]
    old_settings = {key: device.adb("shell", "settings", "get", "global", key) for key in settings}
    old_display = {key: device.adb("shell", "wm", key) for key in ["size", "density"]}
    result = {"status": "RUNNING", "api": device.api, "avd": args.avd, "windows": [],
              "startedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
              "fingerprint": device.adb("shell", "getprop", "ro.build.fingerprint"),
              "runnerSources": {name: hashlib.sha256((ROOT / "scripts" / name).read_bytes()).hexdigest()
                                for name in ["test-compatibility-window.py", "compatibility_ui.py"]}}
    installed = device.adb("shell", "pm", "path", PACKAGE).splitlines()[0].removeprefix("package:")
    # Hash the large debug APK on-device; a broken exec-out stream can be partial
    # despite exit zero. Require the expected APK before changing any settings.
    installed_digest = device.adb("shell", "sha256sum", installed)
    result["apkSha256"] = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    (args.output / "installed-apk.sha256").write_text(installed_digest + "\n")
    assert installed_digest.split()[0] == result["apkSha256"], "Installed APK differs from the fixed candidate"
    result.update(originalSettings=old_settings, originalDisplay=old_display)

    def boot():
        device.adb("reboot")
        device.adb("wait-for-disconnect", timeout=30)
        device.adb("wait-for-device", timeout=180)
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            if device.adb("shell", "getprop", "sys.boot_completed") == "1":
                device.adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
                device.adb("shell", "wm", "dismiss-keyguard")
                return
            time.sleep(2)
        raise AssertionError("Boot did not complete")

    try:
        for key in settings:
            device.adb("shell", "settings", "put", "global", key, "1")
        device.adb("shell", "wm", "size", "1600x2560")
        device.adb("shell", "wm", "density", "240")
        boot()
        device.adb("shell", "am", "force-stop", PACKAGE)
        device.adb("shell", "am", "start", "-W", "--windowingMode", "5", "-n", PACKAGE + "/com.poyal.perilog.MainActivity")
        device.wait_text("재고"); device.tap("재고")
        device.wait_text("재고 관리")
        result["navigationAttempts"] = 0
        for _ in range(3):
            if device.find(text="+ 품목 추가"):
                break
            # A cold freeform window can consume an injected navigation tap
            # while its layout/focus settles. Repeat only this harmless route
            # change from the verified inventory screen, never form submission.
            assert device.find(text="재고 관리"), "Unexpected screen before product navigation"
            device.tap("품목 관리 · 색상", scroll=True)
            result["navigationAttempts"] += 1
            time.sleep(.5)
        device.wait_text("+ 품목 추가")
        device.tap("+ 품목 추가")
        device.tap(kind="android.widget.EditText")
        # Let the IME attach before sending separate keystrokes. A single shell
        # input burst can race its initial selection update on older images.
        time.sleep(.7)
        for character in "WindowResizeExample":
            device.adb("shell", "input", "text", character)
            time.sleep(.08)
        device.wait_text("WindowResizeExample")
        top = device.adb("shell", "dumpsys", "activity", "top")
        task = re.search(r"TASK (?:\d+:)?" + re.escape(PACKAGE) + r"\s+id=(\d+)", top)
        assert task, "App task not found"
        for label, bounds in [("wide", [100, 100, 1480, 1900]), ("narrow", [100, 100, 700, 1250]), ("wide-restored", [100, 100, 1480, 1900])]:
            device.adb("shell", "am", "task", "resize", task[1], *map(str, bounds))
            time.sleep(1)
            report = device.adb("shell", "dumpsys", "activity", "activities", PACKAGE)
            (args.output / (label + "-activity.txt")).write_text(report)
            activities = [block for block in re.split(r"\n\s*\* Hist\s+#\d+:", report)[1:]
                          if PACKAGE + "/com.poyal.perilog.MainActivity" in block.splitlines()[0]
                          and " t" + task[1] + "}" in block.splitlines()[0]]
            assert len(activities) == 1, "Expected one matching app activity"
            current = re.search(r"^\s*CurrentConfiguration=(.*)$", activities[0], re.M)
            assert current and "mWindowingMode=freeform" in current[1], "Task did not enter freeform mode"
            rectangle = r"mBounds=Rect\((-?\d+), (-?\d+) - (-?\d+), (-?\d+)\)"
            task_block = re.search(r"^\s*\* Task\{[^\n]* #" + task[1] + r"\b[^\n]*\n([^*]*)", report, re.M)
            applied = re.search(rectangle, task_block[1]) if task_block else None
            assert applied and list(map(int, applied.groups())) == bounds, "Requested task bounds were not applied"
            # Freeform captions can offset the activity's bounds from its task.
            # Verify task coordinates and the actual content size independently.
            actual = re.search(rectangle, current[1])
            assert actual, "Activity bounds were not reported"
            app_bounds = list(map(int, actual.groups()))
            app_size = [app_bounds[2] - app_bounds[0], app_bounds[3] - app_bounds[1]]
            assert all(size > 0 for size in app_size), "Activity has no visible area"
            if label == "narrow":
                assert all(now < before for now, before in zip(app_size, result["windows"][0]["appSize"])), "Activity did not shrink in both dimensions"
            elif label == "wide-restored":
                assert app_size == result["windows"][0]["appSize"], "Activity did not regain its original size"
            device.wait_text("WindowResizeExample")
            device.capture(label)
            result["windows"].append({"name": label, "bounds": bounds, "appBounds": app_bounds,
                                      "appSize": app_size, "formPreserved": True})
        device.back()
        if not device.find(text="변경 내용을 버릴까요?"):
            device.back()  # The first back can dismiss the keyboard.
        device.wait_text("변경 내용을 버릴까요?"); device.tap("확인")
        assert not device.find(text="WindowResizeExample"), "Unsaved form was not discarded"
        result["status"] = "PASS"
    except BaseException as error:
        result.update(status="FAIL", error=str(error))
        try:
            device.capture("failure")
        except Exception:
            pass
        raise
    finally:
        restoration_errors = []
        for key, value in old_settings.items():
            try:
                device.adb("shell", "settings", "delete", "global", key) if value == "null" else device.adb("shell", "settings", "put", "global", key, value)
            except Exception as error:
                restoration_errors.append(key + ": " + str(error))
        for key, value in old_display.items():
            override = re.search(r"Override (?:size|density): (\S+)", value)
            try:
                device.adb("shell", "wm", key, override[1] if override else "reset")
            except Exception as error:
                restoration_errors.append(key + ": " + str(error))
        if restoration_errors:
            result.update(status="FAIL", restorationErrors=restoration_errors)
        result["finishedAt"] = dt.datetime.now(dt.timezone.utc).isoformat()
        (args.output / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    if result["status"] != "PASS":
        raise SystemExit(1)
    print("PASS: real freeform task resized twice; unsaved form preserved and discarded")


if __name__ == "__main__":
    main()

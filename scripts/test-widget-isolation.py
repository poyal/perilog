#!/usr/bin/env python3
"""Minified APK widget identity/upgrade regression on an explicitly selected emulator.

Build :widget-test-host:assembleDebug -PwidgetHost and :app:assembleRelease first.
This host runs outside Perilog, so killing Perilog does not destroy the test host.
Only synthetic/disposable emulators may be selected. No app data is cleared.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import struct
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.poyal.perilog"
HOST = "com.poyal.perilog.widgettest"


def verify_provider_classes(apk):
    """Read defined DEX classes, not just string literals left by reflection."""
    names = set()
    with zipfile.ZipFile(apk) as archive:
        for entry in archive.namelist():
            if not entry.startswith("classes") or not entry.endswith(".dex"):
                continue
            data = archive.read(entry)
            def u32(offset): return struct.unpack_from("<I", data, offset)[0]
            strings = []
            for index in range(u32(56)):
                offset = u32(u32(60) + index * 4)
                while data[offset] & 128: offset += 1
                offset += 1
                strings.append(data[offset:data.index(0, offset)].decode("utf-8", errors="replace"))
            types = [strings[u32(u32(68) + index * 4)] for index in range(u32(64))]
            names.update(types[u32(u32(100) + index * 32)] for index in range(u32(96)))
    required = {"Lcom/poyal/perilog/widget/DailyRecordWidget;", "Lcom/poyal/perilog/widget/CompactRecordWidget;", "Lcom/poyal/perilog/widget/AppointmentWidget;"}
    if not required <= names:
        raise AssertionError("Release DEX does not preserve all three distinct widget provider classes")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--previous-apk", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sleep-seconds", type=int, default=180)
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("A disposable emulator serial is required")
    args.output.mkdir(parents=True, exist_ok=False)
    verify_provider_classes(args.apk)
    adb = str(Path(os.environ.get("ANDROID_HOME", ROOT / ".tools/android-sdk")) / "platform-tools/adb")

    def run(*command, check=True):
        result = subprocess.run([adb, "-s", args.serial, *command], capture_output=True, text=True)
        if check and result.returncode:
            raise RuntimeError("ADB failed: " + " ".join(command) + "\n" + result.stderr + result.stdout)
        return result.stdout.strip()

    def start(mode="both", command="mount"):
        run("shell", "am", "start", "-n", HOST + "/.HostActivity", "--es", "mode", mode,
            "--es", "command", command, "--es", "target", PACKAGE)

    def read_report():
        raw = run("exec-out", "run-as", HOST, "cat", "files/report.json", check=False)
        return json.loads(raw)

    def valid(report, count):
        if len(report["widgets"]) != count or time.time() * 1000 - report["at"] > 5000:
            return False
        for widget in report["widgets"]:
            appointment = widget["receiver"].endswith("AppointmentWidgetReceiver")
            texts = widget["texts"]
            if appointment and ("병원 일정" not in texts or any("어제·오늘" in s for s in texts)):
                return False
            if not appointment and (not any("어제·오늘" in s for s in texts) or "병원 일정" in texts):
                return False
        return True

    def render_versions():
        return {w["id"]: w["renderedAt"] for w in read_report()["widgets"]}

    def await_report(label, count, require_identity=True, previous_renders=None):
        deadline = time.monotonic() + 60
        consecutive = 0
        report = {}
        while time.monotonic() < deadline:
            try:
                report = read_report()
                ready = valid(report, count) if require_identity else len(report["widgets"]) == count and all(w["texts"] for w in report["widgets"])
                if previous_renders is not None:
                    # Compare timestamps from the same emulator clock. The host
                    # computer clock can be hundreds of milliseconds ahead.
                    ready = ready and all(w["renderedAt"] > previous_renders[w["id"]] for w in report["widgets"])
                consecutive = consecutive + 1 if ready else 0
                if consecutive >= 10:
                    (args.output / (label + ".json")).write_text(json.dumps(report, ensure_ascii=False, indent=2))
                    print(label + ": " + ("PASS" if require_identity else "captured"), flush=True)
                    return report
            except (ValueError, KeyError):
                pass
            time.sleep(.5)
        (args.output / (label + "-failure.json")).write_text(json.dumps(report, ensure_ascii=False, indent=2))
        raise AssertionError(label + ": widget content/identity missing or incorrect")

    def cold_refresh(label, count, mode):
        old = run("shell", "pidof", PACKAGE, check=False).split()
        if not old:
            raise AssertionError("No live Perilog process to terminate")
        run("shell", "kill", "-9", *old)
        time.sleep(1)
        if set(old) & set(run("shell", "pidof", PACKAGE, check=False).split()):
            raise AssertionError("Previous process is still running")
        previous_renders = render_versions()
        start(mode, "refresh")
        report = await_report(label, count, previous_renders=previous_renders)
        new = run("shell", "pidof", PACKAGE, check=False).split()
        if not new or set(old) & set(new):
            raise AssertionError("Expected a new process after refresh")
        (args.output / (label + "-pids.json")).write_text(json.dumps({"before": old, "after": new}))
        return report

    host_apk = ROOT / "widget-test-host/build/outputs/apk/debug/widget-test-host-debug.apk"
    run("root"); run("wait-for-device")
    run("install", "-r", str(host_apk))
    run("shell", "appwidget", "grantbind", "--package", HOST, "--user", "0")
    before = None
    if args.previous_apk:
        run("install", "-r", str(args.previous_apk))
        run("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
        start()
        before = await_report("previous-release", 3, require_identity=False)
        previous_pids = run("shell", "pidof", PACKAGE, check=False).split()
        if previous_pids:
            run("shell", "kill", "-9", *previous_pids)
            time.sleep(1)
            start(command="refresh")
            time.sleep(5)
            await_report("previous-release-cold", 3, require_identity=False)
    run("install", "-r", str(args.apk))
    run("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
    start()
    upgraded = await_report("upgraded-or-fresh", 3)
    if before and [w["id"] for w in before["widgets"]] != [w["id"] for w in upgraded["widgets"]]:
        raise AssertionError("Upgrade replaced existing widget IDs")
    for i in range(3): cold_refresh("mixed-cold-" + str(i + 1), 3, "both")
    run("shell", "input", "keyevent", "KEYCODE_SLEEP")
    time.sleep(max(0, args.sleep_seconds))
    run("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    run("shell", "wm", "dismiss-keyguard")
    previous_renders = render_versions()
    start(command="refresh")
    await_report("after-screen-off", 3, previous_renders=previous_renders)
    previous_renders = render_versions()
    run("shell", "am", "broadcast", "-a", "android.intent.action.TIME_SET", "-p", PACKAGE)
    await_report("after-clock-broadcast", 3, previous_renders=previous_renders)
    run("reboot"); run("wait-for-device")
    deadline = time.monotonic() + 120
    while run("shell", "getprop", "sys.boot_completed") != "1":
        if time.monotonic() >= deadline: raise AssertionError("Emulator did not reboot")
        time.sleep(1)
    # Emulator adbd returns to the shell UID on reboot; restore the test privilege
    # before the final real process termination (never use force-stop here).
    run("root"); run("wait-for-device")
    run("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    run("shell", "wm", "dismiss-keyguard")
    start()
    rebooted = await_report("after-reboot", 3)
    if [w["id"] for w in rebooted["widgets"]] != [w["id"] for w in upgraded["widgets"]]:
        raise AssertionError("Reboot replaced widget IDs")
    start("appointment")
    await_report("appointment-only", 1)
    cold_refresh("appointment-only-cold", 1, "appointment")
    start("all")
    await_report("all-providers-including-compact", 4)
    cold_refresh("all-providers-cold", 4, "all")
    print("Widget isolation and process recovery passed", flush=True)


if __name__ == "__main__":
    main()

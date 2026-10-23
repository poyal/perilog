#!/usr/bin/env python3
"""Run already-built APKs on a named disposable AVD; fail on missing/skipped tests."""
import argparse
import datetime as dt
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tarfile
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.poyal.perilog.debug"
FULL = ["AppFlowTest", "AppointmentFlowTest", "CalendarFlowTest", "UiComponentsTest", "TemporalControlsTest",
        "TopSnackbarHostTest", "ReplenishmentFlowTest", "StockForecastFlowTest", "WidgetFlowTest",
        "SettingsFlowTest", "PrivacyFlowTest", "UpdatesFlowTest", "UsabilityFlowTest"]
CORE = [
    "AppFlowTest#machineEntryAndAdditionalUseWorkOffline",
    "AppFlowTest#deletingRecordCancelsUsageAndUndoRestoresItWithoutAllocationWarnings",
    "AppFlowTest#productAndBulkReceiptCanBeCreatedEditedAndCancelled",
    "AppFlowTest#backupRoundTripAndBrokenFilePreserveData",
    "AppFlowTest#typedDraftSurvivesActivityRecreationRotationAndDoesNotConsumeStock",
    "AppFlowTest#aboutWorksOfflineAndDarkThemeSurvivesRecreation",
    "AppFlowTest#newRecordsStartEmptyAndDateChangesKeepDraftInputs",
    "AppointmentFlowTest#registerMultipleDepartmentsCareAndContactThenShowOnHome",
    "AppointmentFlowTest#newDataFullFileBackupRestoresAfterReset",
    "CalendarFlowTest", "PrivacyFlowTest",
    "SettingsFlowTest#osSettingsAndCancelledFilePickersReturnWithoutChangingAppData",
    "SettingsFlowTest#timeDialogConfirmationAndSaveRemainReachableInBothOrientations",
    "SettingsFlowTest#settingsSaveIndependentlyAndInvalidInputSurvives",
    "WidgetFlowTest#resizingSameRecordWidgetImmediatelyReflowsWithoutRefresh",
    "WidgetFlowTest#realRecordWidgetShowsOnlyThreeStagesRefreshesAndOpensDisplayedYesterday",
    "WidgetFlowTest#realAppointmentWidgetShowsEachTimeAndRemovedAppointmentOpensList",
    "UpdatesFlowTest",
]
LAYOUT = ["UiComponentsTest", "TemporalControlsTest", "AppFlowTest#newRecordsStartEmptyAndDateChangesKeepDraftInputs",
          "SettingsFlowTest#allSettingsOpenDirectlyAndBackRestoresTheListWithoutChangingData",
          "WidgetFlowTest#adaptiveHostSizesKeepAllContentAndTallVisitsUseSingleColumn"]
API30_REGRESSION = ["CalendarFlowTest#providerPreservesUnrelatedEventsAndReminders",
                    "WidgetFlowTest#resizingSameRecordWidgetImmediatelyReflowsWithoutRefresh",
                    "SettingsFlowTest#osSettingsAndCancelledFilePickersReturnWithoutChangingAppData",
                    "PrivacyFlowTest#lockOnlyAuthenticatesOnReturnAndCancelNeverExposesContent"]
STARTUP_INPUT = ["AppFlowTest#newRecordsStartEmptyAndDateChangesKeepDraftInputs",
                 "AppFlowTest#invalidWholeQuantityCannotSaveThenCanBeCorrected"]
OS_TRANSITIONS = ["AppFlowTest#newRecordsStartEmptyAndDateChangesKeepDraftInputs",
                  "PrivacyFlowTest#lockOnlyAuthenticatesOnReturnAndCancelNeverExposesContent",
                  "PrivacyFlowTest#allFourCombinationsControlScreenshotsRecordingsAndDialogProtection",
                  "SettingsFlowTest#osSettingsAndCancelledFilePickersReturnWithoutChangingAppData"]


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def expected_tests(targets, source=ROOT / "app/src/androidTest/java/com/poyal/perilog"):
    expected = set()
    for target in targets:
        cls, _, method = target.partition("#")
        names = set(re.findall(r"@Test\s+fun\s+(\w+)\s*\(", (source / (cls + ".kt")).read_text()))
        if not names or method and method not in names:
            raise ValueError("Unknown or empty test selection: " + target)
        expected.update(("com.poyal.perilog." + cls, name) for name in names if not method or name == method)
    return expected


def parse_instrumentation(output, expected, exit_code):
    """ADB can exit zero even when the app crashed. Require every terminal result."""
    current, results, last_key = {}, [], None
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, _, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
            current[key] = value
            last_key = key
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = int(line.rsplit(":", 1)[1])
            if code <= 0 and "class" in current and "test" in current:
                results.append({"class": current["class"], "test": current["test"],
                                "code": code, "stack": current.get("stack", "")})
            current, last_key = {}, None
        elif last_key == "stack" and not line.startswith("INSTRUMENTATION_"):
            current[last_key] += "\n" + line
    actual = [(r["class"], r["test"]) for r in results]
    problems = []
    if exit_code:
        problems.append("instrumentation command exit code: " + str(exit_code))
    if not re.search(r"^INSTRUMENTATION_CODE: -1\s*$", output, re.M):
        problems.append("normal instrumentation completion missing")
    if not re.search(r"^OK \(\d+ tests?\)\s*$", output, re.M):
        problems.append("JUnit success summary missing")
    if len(actual) != len(set(actual)):
        problems.append("duplicate test results")
    if set(actual) != expected:
        problems.append("test inventory mismatch")
    if any(r["code"] != 0 for r in results):
        problems.append("failed, errored or skipped test")
    return {"status": "FAIL" if problems else "PASS", "problems": problems,
            "expected": len(expected), "executed": len(actual), "results": results,
            "missing": sorted(expected - set(actual)), "unexpected": sorted(set(actual) - expected)}


def write_junit(path, report):
    root = ET.Element("testsuite", name="Android compatibility", tests=str(report["executed"]),
                      failures=str(sum(r["code"] != 0 for r in report["results"])),
                      errors=str(bool(report["problems"])), skipped="0")
    for result in report["results"]:
        case = ET.SubElement(root, "testcase", classname=result["class"], name=result["test"])
        if result["code"] != 0:
            ET.SubElement(case, "failure", message="instrumentation status " + str(result["code"])).text = result["stack"]
    if report["problems"]:
        ET.SubElement(root, "system-err").text = "\n".join(report["problems"])
    ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--avd", required=True, help="Must start with perilog_compat_")
    parser.add_argument("--api", required=True, type=int)
    parser.add_argument("--suite", choices=["full", "core", "permissions", "layout", "calendar", "api30-regression", "startup-input", "os-transitions", "privacy-lock", "keyboard", "widgets", "widget-landscape"], default="full")
    parser.add_argument("--profile", choices=["normal", "large", "all"], default="all")
    parser.add_argument("--layout", choices=["phone", "compact", "tablet"], default="phone")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--apk", type=Path, default=ROOT / "app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--test-apk", type=Path, default=ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial) or not args.avd.startswith("perilog_compat_"):
        parser.error("Only explicitly named perilog_compat_ disposable emulators are allowed")
    adb = str(Path(os.environ.get("ANDROID_HOME", ROOT / ".tools/android-sdk")) / "platform-tools/adb")

    def run(*command, check=True, timeout=60):
        result = subprocess.run([adb, "-s", args.serial, *command], capture_output=True, text=True, timeout=timeout)
        if check and result.returncode:
            raise RuntimeError("ADB failed: " + " ".join(command) + "\n" + result.stdout + result.stderr)
        return result

    def shell(*command, **kwargs):
        return run("shell", *command, **kwargs).stdout.strip()

    actual_avd = run("emu", "avd", "name").stdout.splitlines()[0]
    if actual_avd != args.avd or shell("getprop", "ro.kernel.qemu") != "1":
        raise RuntimeError("Unexpected emulator; refusing to change data")
    if int(shell("getprop", "ro.build.version.sdk")) != args.api:
        raise RuntimeError("Actual API differs from requested API")
    if shell("getprop", "sys.boot_completed") != "1":
        raise RuntimeError("Emulator is not booted")
    targets = {"full": FULL, "core": CORE, "permissions": ["CalendarPermissionFlowTest"], "layout": LAYOUT,
               "calendar": ["CalendarFlowTest"], "api30-regression": API30_REGRESSION, "startup-input": STARTUP_INPUT,
               "os-transitions": OS_TRANSITIONS,
               "privacy-lock": ["PrivacyFlowTest#lockOnlyAuthenticatesOnReturnAndCancelNeverExposesContent"],
               "keyboard": ["AppFlowTest#typedDraftSurvivesActivityRecreationRotationAndDoesNotConsumeStock",
                            "SettingsFlowTest#timeDialogConfirmationAndSaveRemainReachableInBothOrientations"],
               "widgets": ["WidgetFlowTest"],
               "widget-landscape": ["WidgetFlowTest#landscapeHostsKeepEveryStageAndCareVisible"]}[args.suite]
    expected = expected_tests(targets)
    artifacts = {str(p.resolve()): sha256(p) for p in [args.apk, args.test_apk]}
    args.output.mkdir(parents=True, exist_ok=False)
    metadata = {"startedAt": dt.datetime.now(dt.timezone.utc).isoformat(), "api": args.api,
                "avd": actual_avd, "serial": args.serial, "suite": args.suite, "layout": args.layout,
                "abi": shell("getprop", "ro.product.cpu.abi"), "pageSize": shell("getconf", "PAGE_SIZE"),
                "fingerprint": shell("getprop", "ro.build.fingerprint"), "artifacts": artifacts,
                "runnerSha256": sha256(__file__),
                "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
                "workingTree": subprocess.check_output(["git", "status", "--short"], cwd=ROOT, text=True),
                "expectedTests": sorted(expected), "profiles": [], "status": "RUNNING",
                "workingTestSources": {cls: sha256(ROOT / "app/src/androidTest/java/com/poyal/perilog" / (cls + ".kt"))
                                for cls in sorted({target.split("#")[0] for target in targets})}}
    report_file = args.output / "result.json"

    def save():
        report_file.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")

    settings = [("system", "font_scale"), ("system", "screen_off_timeout"),
                ("global", "stay_on_while_plugged_in"), ("global", "airplane_mode_on"),
                ("secure", "show_ime_with_hard_keyboard")]
    original = {(ns, key): shell("settings", "get", ns, key) for ns, key in settings}
    original_display = {key: shell("wm", key) for key in ["size", "density"]}
    save()
    try:
        for apk in [args.apk, args.test_apk]:
            run("install", "-r", str(apk.resolve()), timeout=180)
        shell("settings", "put", "system", "screen_off_timeout", "2147483647")
        shell("svc", "power", "stayon", "true")
        shell("input", "keyevent", "KEYCODE_WAKEUP")
        shell("wm", "dismiss-keyguard")
        shell("cmd", "connectivity", "airplane-mode", "enable")
        shell("settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
        if args.layout == "compact":
            shell("wm", "size", "1080x2340"); shell("wm", "density", "480")
        elif args.layout == "tablet":
            shell("wm", "size", "1600x2560"); shell("wm", "density", "240")
        profiles = ["normal", "large"] if args.profile == "all" else [args.profile]
        for profile in profiles:
            output = args.output / profile
            output.mkdir()
            shell("am", "force-stop", PACKAGE)
            if shell("pm", "clear", PACKAGE) != "Success":
                raise RuntimeError("Could not reset the disposable debug fixture")
            # A previous process-restoration check may have used adb root. Its
            # screenrecord files are then unreadable to UiAutomation's shell UID.
            # Remove only our four known recordings, also preventing stale frames
            # from satisfying a subsequent capture assertion.
            recordings = [f"/data/local/tmp/lock-{lock}-protection-{protection}.mp4"
                          for lock in ["false", "true"] for protection in ["false", "true"]]
            shell("rm", "-f", *recordings)
            if any(target.split("#")[0] == "CalendarFlowTest" for target in targets):
                # These tests insert a synthetic calendar after granting access.
                # A provider can silently reject insert() while the permission's
                # AppOp is still changing. Require both grants before fixture
                # creation. The separate permission-denial suite stays ungranted.
                operations = ["READ_CALENDAR", "WRITE_CALENDAR"]
                for operation in operations:
                    shell("pm", "grant", PACKAGE, "android.permission." + operation)
                deadline = time.monotonic() + 20
                while True:
                    access = {op: shell("cmd", "appops", "get", PACKAGE, op) for op in operations}
                    if all(op + ": allow" in state for op, state in access.items()):
                        (output / "calendar-fixture-access.json").write_text(json.dumps(access, indent=2) + "\n")
                        break
                    if time.monotonic() >= deadline:
                        raise RuntimeError("Calendar fixture AppOps did not become ready: " + str(access))
                    time.sleep(.2)
            shell("settings", "put", "system", "font_scale", "1.5" if profile == "large" else "1.0")
            selector = ",".join("com.poyal.perilog." + target for target in targets)
            command = [adb, "-s", args.serial, "shell", "am", "instrument", "-w", "-r", "-e", "class", selector,
                       "-e", "timeout_msec", "120000", PACKAGE + ".test/com.poyal.perilog.PerilogTestRunner"]
            print(f"API {args.api} {args.suite} {profile}: {len(expected)} tests", flush=True)
            started = time.monotonic()
            with (output / "instrumentation.log").open("w") as log:
                process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, text=True)
                try:
                    code = process.wait(timeout=2400)
                except BaseException:
                    process.kill(); process.wait()
                    shell("am", "force-stop", PACKAGE, check=False)
                    raise
            raw = (output / "instrumentation.log").read_text()
            report = parse_instrumentation(raw, expected, code)
            report.update(profile=profile, seconds=round(time.monotonic() - started, 2), command=command)
            (output / "result.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
            write_junit(output / "tests.xml", report)
            (output / "logcat.txt").write_text(run("logcat", "-d", "-t", "3500", check=False).stdout)
            with (output / "screen.png").open("wb") as screenshot:
                subprocess.run([adb, "-s", args.serial, "exec-out", "screencap", "-p"], stdout=screenshot, check=True, timeout=30)
            for folder in ["e2e-artifacts", "calendar-screenshots"]:
                if run("shell", "run-as", PACKAGE, "ls", "-d", "files/" + folder, check=False).returncode:
                    continue  # This test selection may not produce optional captures.
                result = subprocess.run([adb, "-s", args.serial, "exec-out", "run-as", PACKAGE,
                                         "tar", "-cf", "-", "files/" + folder], capture_output=True, timeout=60)
                result.check_returncode()
                # exec-out can mix remote stderr into stdout and still exit zero.
                # An error message padded with tar EOF blocks is not an archive.
                with tarfile.open(fileobj=io.BytesIO(result.stdout)) as archive:
                    if not archive.getmembers():
                        raise RuntimeError("Empty capture archive: " + folder)
                (output / (folder + ".tar")).write_bytes(result.stdout)
            metadata["profiles"].append(report)
            save()
            print(f"API {args.api} {profile}: {report['status']} ({report['seconds']}s)", flush=True)
            if report["status"] != "PASS":
                raise RuntimeError("; ".join(report["problems"]))
        if artifacts != {str(p.resolve()): sha256(p) for p in [args.apk, args.test_apk]}:
            raise RuntimeError("APK changed during verification")
        metadata["status"] = "PASS"
    except BaseException as error:
        metadata.update(status="FAIL", error=str(error))
        raise
    finally:
        restoration_errors = []
        if args.layout != "phone":
            for key, value in original_display.items():
                override = re.search(r"Override (?:size|density): (\S+)", value)
                restored = run("shell", "wm", key, override[1] if override else "reset", check=False)
                if restored.returncode:
                    restoration_errors.append("display_" + key)
        for (namespace, key), value in original.items():
            result = run("shell", "settings", "delete", namespace, key, check=False) if value == "null" else run(
                "shell", "settings", "put", namespace, key, value, check=False)
            if result.returncode:
                restoration_errors.append(key)
        result = run("shell", "cmd", "connectivity", "airplane-mode",
                     "enable" if original[("global", "airplane_mode_on")] == "1" else "disable", check=False)
        if result.returncode:
            restoration_errors.append("airplane_mode_on")
        if restoration_errors:
            metadata.update(status="FAIL", restorationErrors=restoration_errors)
        metadata["finishedAt"] = dt.datetime.now(dt.timezone.utc).isoformat()
        save()
    return 0 if metadata["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())

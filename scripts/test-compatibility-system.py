#!/usr/bin/env python3
"""Signed APK install/upgrade, OS permissions, SAF backup/restore on an isolated AVD."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
from compatibility_ui import Device

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.poyal.perilog"


def fixture():
    today = dt.date.today().isoformat()
    item = {"productId": "compat-product", "name": "Compatibility sample", "quantity": 2}
    treatment = {"id": "compat-record", "date": today, "kind": "MACHINE", "weightGrams": 62300,
                 "machineUf": -120, "initialDrain": 0, "saved": True, "usageConfirmed": True,
                 "items": [item], "memo": "Synthetic compatibility sample", "createdAt": 100, "updatedAt": 100}
    return {"formatVersion": 1, "products": [{"id": item["productId"], "name": item["name"]}],
            "templates": [{"id": "compat-template", "name": "Compatibility template", "items": [item]}],
            "treatments": [treatment], "usages": [{"id": treatment["id"], "date": today, "items": [item], "kind": "MACHINE", "createdAt": 100}],
            "receipts": [{"id": "compat-receipt", "date": today, "createdAt": 10, "lines": [{"id": "compat-line", "productId": item["productId"], "quantity": 20}]}],
            "counts": [{"id": "compat-count", "date": today, "createdAt": 20, "productId": item["productId"], "quantity": 15}],
            "audit": [{"id": "compat-audit", "at": 100, "action": "compatibility", "targetId": item["productId"], "before": "20", "after": "15"}],
            "drafts": [{"id": "compat-draft", "treatment": {**treatment, "id": "compat-draft", "saved": False, "usageConfirmed": False, "weightGrams": 64000}}],
            "preferences": {"darkMode": "LIGHT", "reminderHour": 18, "reminderMinute": 25, "backupDays": 7, "keepBackups": 17},
            "departments": [{"id": "compat-dept", "name": "Compatibility department"}],
            "appointments": [{"id": "compat-visit", "date": today, "time": "09:00", "memo": "Synthetic appointment"}],
            "contacts": [{"id": "compat-contact", "name": "Compatibility contact", "phone": "01000000000", "allowCall": False, "allowSms": False}]}


def protobuf_fields(buf):
    def varint(pos):
        value, shift = 0, 0
        while True:
            byte = buf[pos]; pos += 1; value |= (byte & 127) << shift
            if byte < 128:
                return value, pos
            shift += 7
    pos, result = 0, []
    while pos < len(buf):
        key, pos = varint(pos)
        wire = key & 7
        if wire == 0:
            value, pos = varint(pos)
        elif wire in (1, 2, 5):
            if wire == 2:
                size, pos = varint(pos)
            else:
                size = 8 if wire == 1 else 4
            value = buf[pos:pos+size]; pos += size
        else:
            raise ValueError("Unknown protobuf wire type")
        result.append((key >> 3, value))
    return result


def preferences(path):
    result = {}
    for field, entry in protobuf_fields(path.read_bytes()):
        if field != 1:
            raise ValueError("Unknown preferences format")
        row = dict(protobuf_fields(entry))
        result[row[1].decode()] = row[2].hex()
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--avd", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--previous-apk", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--reset-fixture", action="store_true", help="Remove only synthetic release data on this named compatibility AVD before testing")
    args = parser.parse_args()
    sdk = Path(os.environ.get("ANDROID_HOME", ROOT / ".tools/android-sdk"))
    device = Device(sdk / "platform-tools/adb", args.serial, args.avd, args.output)
    args.output.mkdir(parents=True, exist_ok=False)
    result = {"status": "RUNNING", "startedAt": dt.datetime.now(dt.timezone.utc).isoformat(), "avd": args.avd,
              "api": int(device.adb("shell", "getprop", "ro.build.version.sdk")),
              "fingerprint": device.adb("shell", "getprop", "ro.build.fingerprint"),
              "abi": device.adb("shell", "getprop", "ro.product.cpu.abi"),
              "runnerSources": {name: hashlib.sha256((ROOT / "scripts" / name).read_bytes()).hexdigest()
                                for name in ["test-compatibility-system.py", "compatibility_ui.py"]},
              "pageSize": int(device.adb("shell", "getconf", "PAGE_SIZE")),
              "apkSha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(), "checks": []}
    env = {**os.environ, "JAVA_HOME": str(ROOT / ".tools/jdk")}

    def check(name):
        result["checks"].append(name)
        (args.output / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2))
        print(name + ": PASS", flush=True)

    def downloads():
        device.tap(desc="Show roots")
        device.tap("Downloads")

    def backup_files(label):
        target = args.output / label
        device.adb("pull", remote_folder, str(target))
        return {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in target.glob("*.json")}

    stamp = args.output.name + "-" + str(int(time.time()))
    remote_fixture = "perilog-compat-fixture-" + stamp + ".json"
    folder_name = "perilog-compat-backups-" + stamp
    remote_folder = "/sdcard/" + folder_name
    original_power = {key: device.adb("shell", "settings", "get", namespace, key)
                      for namespace, key in [("system", "screen_off_timeout"), ("global", "stay_on_while_plugged_in")]}
    try:
        certificates = []
        for label, apk in [("candidate", args.apk)] + ([("previous", args.previous_apk)] if args.previous_apk else []):
            report = subprocess.check_output([str(sdk / "build-tools/36.0.0/apksigner"), "verify", "--verbose", "--print-certs", str(apk)], text=True, env=env)
            (args.output / (label + "-signature.txt")).write_text(report)
            certificates.append(re.search(r"Signer #1 certificate SHA-256 digest: (\w+)", report)[1])
        assert len(set(certificates)) == 1, "Update signing certificate differs"
        result["certificateSha256"] = certificates[0]
        device.adb("root"); device.adb("wait-for-device"); time.sleep(1)
        device.adb("shell", "settings", "put", "system", "screen_off_timeout", "2147483647")
        device.adb("shell", "svc", "power", "stayon", "true")
        device.adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        device.adb("shell", "wm", "dismiss-keyguard")
        if "package:" + PACKAGE in device.adb("shell", "pm", "list", "packages", PACKAGE).splitlines():
            assert args.reset_fixture, "Existing release fixture; select --reset-fixture only on this disposable AVD"
            device.adb("uninstall", PACKAGE)
        device.adb("install", str(args.previous_apk or args.apk), timeout=180)
        device.start()
        if result["api"] >= 33:
            device.tap(resource="id/permission_deny_button")
            device.tap(desc="설정"); device.tap("알림", scroll=True)
            device.wait_text("시스템 알림이 꺼져 있어요")
            device.adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
            device.settings(); device.tap("알림", scroll=True)
            device.wait_text("시스템 알림이 켜져 있어요")
            device.adb("shell", "pm", "revoke", PACKAGE, "android.permission.POST_NOTIFICATIONS")
            device.settings(); device.tap("알림", scroll=True)
            device.wait_text("시스템 알림이 꺼져 있어요")
            device.adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
            check("notification-deny-grant-revoke-regrant")
        else:
            device.tap(desc="설정"); device.tap("알림", scroll=True)
            device.wait_text("시스템 알림이 켜져 있어요")
            check("notification-pre-runtime-permission")
        device.settings(); device.tap("데이터 내보내기·가져오기", scroll=True)
        sample = args.output / "fixture.json"
        sample.write_text(json.dumps(fixture(), ensure_ascii=False, indent=2))
        device.adb("push", str(sample), "/sdcard/Download/" + remote_fixture)
        device.tap("백업 파일 가져오기")
        downloads(); device.tap(remote_fixture, scroll=True)
        device.wait_text("백업으로 전체 복원할까요?"); device.tap("확인")
        time.sleep(1)
        device.settings(); device.tap("자동 백업", scroll=True); device.tap("백업 폴더 선택")
        model = device.adb("shell", "getprop", "ro.product.model")
        if not device.find(text=model):
            device.tap(desc="Show roots")
        device.tap(model)
        # Create through the actual picker, which opens the new directory and
        # avoids selecting a storage root (disallowed on Android 11+).
        device.tap(desc="New folder")
        root = device.tree()
        initial = next(node.get("text", "") for node in root.iter("node") if node.get("class") == "android.widget.EditText")
        device.tap(kind="android.widget.EditText")
        device.adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
        if initial:
            device.adb("shell", "input", "keyevent", *(["KEYCODE_DEL"] * len(initial)))
        device.adb("shell", "input", "text", folder_name)
        device.tap(resource="android:id/button1")
        device.tap("USE THIS FOLDER"); device.tap("ALLOW")
        device.wait_text("백업 폴더 연결됨"); device.wait_text("자동 백업 완료")
        check("saf-import-and-persisted-backup-folder")
        before_files = backup_files("backup-before")
        assert before_files, "No automatic backup"
        latest = max((args.output / "backup-before").glob("*.json"))
        exported = json.loads(latest.read_text())
        assert exported["treatments"][0]["weightGrams"] == 62300
        assert exported["treatments"][0]["machineUf"] == -120
        assert exported["drafts"][0]["treatment"]["weightGrams"] == 64000
        before = device.snapshot("before")
        assert before["integrity"] == "ok"
        for table in ["products", "templates", "treatments", "usages", "receipts", "counts", "audit", "drafts", "settings"]:
            assert before["tables"][table], "Empty required fixture table: " + table
        if args.previous_apk:
            result["previousApkSha256"] = hashlib.sha256(args.previous_apk.read_bytes()).hexdigest()
            device.adb("install", "-r", str(args.apk), timeout=180)
        device.start(); device.tap("재고"); device.capture("inventory-after-restart")
        device.tap("기록"); device.capture("records-after-restart")
        after = device.snapshot("after")
        for key in ["uid", "firstInstallTime", "schema", "integrity", "tables"]:
            assert before[key] == after[key], "Preserved data changed: " + key
        assert before["protectedFiles"] and before["protectedFiles"].items() <= after["protectedFiles"].items()
        old_preferences = preferences(args.output / "before/files/datastore/device.preferences_pb")
        new_preferences = preferences(args.output / "after/files/datastore/device.preferences_pb")
        assert {k:v for k,v in old_preferences.items() if k != "appUpdate"} == {k:v for k,v in new_preferences.items() if k != "appUpdate"}
        for phase in ["before", "after"]:
            permissions = (args.output / phase / "uri-permissions.txt").read_text()
            grants = re.split(r"(?=UriPermission\{)", permissions)
            assert any(folder_name in grant and "targetPkg=" + PACKAGE in grant and "persisted=0x3" in grant
                       for grant in grants), "Persisted folder grant missing"
            if result["api"] >= 33:
                assert "android.permission.POST_NOTIFICATIONS: granted=true" in (args.output / phase / "package.txt").read_text()
        check("upgrade-all-db-fields-and-permissions" if args.previous_apk else "fresh-install-and-restart-data-preservation")
        device.settings(); device.tap("자동 백업", scroll=True); device.tap("지금 백업", scroll=True)
        device.wait_text("백업을 완료했어요")
        after_files = backup_files("backup-after")
        assert before_files.items() <= after_files.items() and len(after_files) > len(before_files)
        new_export = json.loads(max((args.output / "backup-after").glob("*.json")).read_text())
        assert {k:v for k,v in exported.items() if k != "exportedAt"} == {k:v for k,v in new_export.items() if k != "exportedAt"}
        check("backup-without-folder-reselection-and-json-preservation")
        files_before_export = set(device.adb("shell", "ls", "-1", "/sdcard/Download").splitlines())
        device.settings(); device.tap("데이터 내보내기·가져오기", scroll=True); device.tap("전체 데이터 내보내기")
        downloads()
        root = device.tree()
        filename = next(node.get("text") for node in root.iter("node") if node.get("class") == "android.widget.EditText")
        assert re.fullmatch(r"[\w.-]+", filename), "Unexpected export filename"
        device.tap("SAVE")
        device.wait_text("파일로 내보냈어요")
        files_after_export = set(device.adb("shell", "ls", "-1", "/sdcard/Download").splitlines())
        # CreateDocument appends " (1)" etc. when a same-day export exists.
        # Read the new document, never accidentally compare a previous run's file.
        created = [name for name in files_after_export - files_before_export
                   if re.fullmatch(re.escape(Path(filename).stem) + r"(?: \(\d+\))?\.json", name)]
        assert len(created) == 1, "Expected exactly one new exported document: " + str(created)
        result["manualExportFile"] = created[0]
        device.adb("pull", "/sdcard/Download/" + created[0], str(args.output / "manual-export.json"))
        manual = json.loads((args.output / "manual-export.json").read_text())
        assert {k:v for k,v in exported.items() if k != "exportedAt"} == {k:v for k,v in manual.items() if k != "exportedAt"}
        check("saf-manual-export")
        installed = device.adb("shell", "pm", "path", PACKAGE).splitlines()[0].removeprefix("package:")
        data = device.adb("exec-out", "cat", installed, binary=True)
        assert hashlib.sha256(data).hexdigest() == result["apkSha256"]
        result.update(status="PASS", schema=after["schema"], tables=len(after["tables"]),
                      rows=sum(len(rows) for rows in after["tables"].values()),
                      fields=sum(len(row) for rows in after["tables"].values() for row in rows))
        check("installed-apk-bytes-match-candidate")
    except BaseException as error:
        result.update(status="FAIL", error=str(error))
        try:
            device.capture("failure")
        except Exception:
            pass
        raise
    finally:
        restoration_errors = []
        for namespace, key in [("system", "screen_off_timeout"), ("global", "stay_on_while_plugged_in")]:
            value = original_power[key]
            try:
                device.adb("shell", "settings", "delete", namespace, key) if value == "null" else device.adb("shell", "settings", "put", namespace, key, value)
            except Exception as error:
                restoration_errors.append(key + ": " + str(error))
        if restoration_errors:
            result.update(status="FAIL", restorationErrors=restoration_errors)
        result["finishedAt"] = dt.datetime.now(dt.timezone.utc).isoformat()
        (args.output / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())

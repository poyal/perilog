#!/usr/bin/env python3
"""Host-driven process recreation check; only opens an unsaved form in the debug app."""
import os
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
SERIAL = os.environ.get('PERILOG_SERIAL', 'emulator-5554')
if not re.fullmatch(r'emulator-\d+', SERIAL):
    raise SystemExit('Use a development emulator.')
ADB = str(pathlib.Path(os.environ.get('ANDROID_HOME', ROOT / '.tools/android-sdk')) / 'platform-tools/adb')
PACKAGE = 'com.poyal.perilog.debug'
OUTPUT = ROOT / '.tools/e2e-results/process'
OUTPUT.mkdir(parents=True, exist_ok=True)
for name in ('failure.xml', 'failure.png', 'result.txt', 'restored-form.png'):
    (OUTPUT / name).unlink(missing_ok=True)


def adb(*args):
    return subprocess.check_output([ADB, '-s', SERIAL, *args], timeout=30).decode().strip()


def tree():
    for _ in range(5):
        result = adb('shell', 'uiautomator', 'dump', '--compressed', '/sdcard/perilog-e2e.xml')
        if 'dumped to' in result:
            return ET.fromstring(adb('shell', 'cat', '/sdcard/perilog-e2e.xml'))
        time.sleep(0.5)
    raise AssertionError('UI hierarchy not ready')


def find(text=None, kind=None):
    for node in tree().iter('node'):
        if (text is None or node.get('text') == text) and (kind is None or node.get('class') == kind):
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds', '')))
            if x2 > x1 and y2 > y1:
                return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap(text):
    point = find(text)
    if point is None:
        raise AssertionError(f'Visible control missing: {text}')
    adb('shell', 'input', 'tap', *map(str, point))


try:
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-n', PACKAGE + '/com.poyal.perilog.MainActivity')
    time.sleep(1)
    tap('재고')
    for _ in range(8):
        if find('품목 관리 · 색상'):
            tap('품목 관리 · 색상')
            break
        if find('첫 품목 등록'):
            tap('첫 품목 등록')
            break
        bounds = tree().find('node').get('bounds')
        _, _, width, height = map(int, re.findall(r'\d+', bounds))
        adb('shell', 'input', 'swipe', str(width//2), str(height*3//4), str(width//2), str(height//3), '250')
    else:
        raise AssertionError('Product management not found')
    tap('+ 품목 추가')
    point = find(kind='android.widget.EditText')
    assert point, 'Product name field missing'
    adb('shell', 'input', 'tap', *map(str, point))
    adb('shell', 'input', 'text', 'ProcessRestoreExample')
    assert find('ProcessRestoreExample', 'android.widget.EditText'), 'Typed input missing'
    before = adb('shell', 'pidof', PACKAGE)
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    time.sleep(1)
    adb('shell', 'am', 'kill', PACKAGE)
    assert subprocess.run([ADB, '-s', SERIAL, 'shell', 'pidof', PACKAGE], capture_output=True).returncode != 0, 'Process did not exit'
    adb('shell', 'am', 'start', '-n', PACKAGE + '/com.poyal.perilog.MainActivity')
    time.sleep(1)
    after = adb('shell', 'pidof', PACKAGE)
    assert before != after, 'Expected a new process'
    assert find('ProcessRestoreExample', 'android.widget.EditText'), 'Unsaved input not restored after process death'
    screenshot = subprocess.check_output([ADB, '-s', SERIAL, 'exec-out', 'screencap', '-p'])
    (OUTPUT / 'restored-form.png').write_bytes(screenshot)
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    # First back may only close the keyboard.
    if not find('변경 내용을 버릴까요?'):
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    tap('확인')
    assert not find('ProcessRestoreExample'), 'Discarded form still visible'
    result = f'PASS: process {before} -> {after}; unsaved product form restored, then discarded.\n'
    (OUTPUT / 'result.txt').write_text(result)
    print(result, end='')
except Exception:
    (OUTPUT / 'failure.xml').write_text(ET.tostring(tree(), encoding='unicode'))
    (OUTPUT / 'failure.png').write_bytes(subprocess.check_output([ADB, '-s', SERIAL, 'exec-out', 'screencap', '-p']))
    raise

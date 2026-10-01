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


def wait_until(condition, message, timeout=20):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        value = condition()
        if value:
            return value
        time.sleep(0.25)
    raise AssertionError(message)


def process_id():
    result = subprocess.run([ADB, '-s', SERIAL, 'shell', 'pidof', PACKAGE],
                            capture_output=True, text=True, timeout=10)
    if result.returncode == 1 and not result.stdout.strip() and not result.stderr.strip():
        return None
    result.check_returncode()
    pid = result.stdout.strip()
    assert re.fullmatch(r'\d+', pid), 'Expected one main app process'
    return pid


def background_state_saved():
    report = adb('shell', 'dumpsys', 'activity', 'activities')
    records = [block for block in re.split(r'\n\s*\* Hist\s+#\d+:', report)[1:]
               if PACKAGE + '/' in block.splitlines()[0]]
    return bool(records) and all(
        re.search(r'\b(?:mState|state)=STOPPED\b', block) and 'mHaveState=true' in block
        for block in records)


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


stage = 'open_product_form'
try:
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/com.poyal.perilog.MainActivity')
    wait_until(lambda: find('재고'), 'App navigation did not appear')
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
    before = process_id()
    assert before, 'Main app process missing before backgrounding'
    stage = 'background_saved_state'
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    wait_until(background_state_saved, 'Activity did not stop and save state')
    stage = 'process_exit'
    # am kill may retain a recently visible process. Kill only this debug app's
    # verified PID under its own UID, after Android has saved the stopped task.
    assert process_id() == before, 'App process changed before the deliberate kill'
    adb('shell', 'run-as', PACKAGE, 'kill', '-9', before)
    wait_until(lambda: process_id() != before, 'Original app process did not exit')
    stage = 'restore_input'
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/com.poyal.perilog.MainActivity')
    wait_until(lambda: find('ProcessRestoreExample', 'android.widget.EditText'),
               'Unsaved input not restored after process death')
    after = process_id()
    assert after, 'Restored app process missing'
    assert before != after, 'Expected a new process'
    screenshot = subprocess.check_output([ADB, '-s', SERIAL, 'exec-out', 'screencap', '-p'])
    (OUTPUT / 'restored-form.png').write_bytes(screenshot)
    stage = 'discard_input'
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    # First back may only close the keyboard.
    if not find('변경 내용을 버릴까요?'):
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    tap('확인')
    assert not find('ProcessRestoreExample'), 'Discarded form still visible'
    result = f'PASS: process {before} -> {after}; unsaved product form restored, then discarded.\n'
    (OUTPUT / 'result.txt').write_text(result)
    print(result, end='')
except Exception as error:
    # Only a fixed stage name and exception type enter the public CI annotation.
    # Detailed captures stay in the normal test artifact.
    print(f'::error title=Process restore check::{stage}: {type(error).__name__}', flush=True)
    try:
        (OUTPUT / 'failure.xml').write_text(ET.tostring(tree(), encoding='unicode'))
        (OUTPUT / 'failure.png').write_bytes(subprocess.check_output([ADB, '-s', SERIAL, 'exec-out', 'screencap', '-p'], timeout=10))
    except Exception:
        print('Failure capture unavailable; preserving the original error.', flush=True)
    raise

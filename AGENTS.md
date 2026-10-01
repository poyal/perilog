# 에뮬레이터 수동 테스트

사용자가 에뮬레이터로 앱을 열어 달라고 요청하면 샘플 데이터 준비도 기본으로 포함한다.

- 물품, 사용 구성, 입고 재고, 지난 2주 기록과 오늘의 활력 상태·사용 구성 예시를 채운다.
- 기존에 입력하거나 수정한 값은 유지한다. 샘플 준비를 위해 앱 데이터를 초기화하지 않는다.
- `:app:assembleDebug :app:assembleDebugAndroidTest -PcaptureScreenshots`로 빌드한다.
- 개발용 에뮬레이터에 두 APK를 설치하고 `adb -s <emulator-serial> shell am instrument -w -e class com.poyal.perilog.capture.PreviewSamples#load com.poyal.perilog.debug.test/androidx.test.runner.AndroidJUnitRunner`로 샘플을 준비한다. 반복 실행은 기존 값과 초안을 보존한다.
- 샘플 준비 후 페리로그 앱을 다시 열고 에뮬레이터 창을 사용자가 직접 조작할 수 있게 남겨 둔다.

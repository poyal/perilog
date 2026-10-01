# 자동 E2E 테스트

실제 앱의 Compose 화면을 누르고 입력한 뒤 Room에 저장된 결과와 재고 잔량을 확인합니다. 외부 API·가짜 서버는 사용하지 않습니다. 테스트 자료는 개발용 패키지 `com.poyal.perilog.debug`에만 넣습니다.

## 실행

개발용 에뮬레이터와 JDK 17, Android SDK 36을 준비합니다. `.tools/`의 로컬 도구가 있으면 빌드 스크립트가 우선 사용합니다.

```sh
# 계산·재고·백업 규칙 및 Android 정적 검사
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug

# 기본 글씨 / 글씨 1.5배 / 두 가지 모두
PERILOG_SERIAL=emulator-5554 ./scripts/test-e2e.sh normal
PERILOG_SERIAL=emulator-5554 ./scripts/test-e2e.sh large
PERILOG_SERIAL=emulator-5554 ./scripts/test-e2e.sh all

# 실제 백그라운드 프로세스 종료 후 미저장 입력 복구
PERILOG_SERIAL=emulator-5554 ./scripts/test-process-restore.py
```

스크립트는 개인 기기 연결을 거부하고 `emulator-*`만 허용합니다. 실행마다 개발용 앱 자료를 초기화하고 비행기 모드를 켭니다. 가상 하드웨어 키보드가 연결돼 있어도 화면 키보드를 표시하도록 설정합니다. 화면을 깨우고 기본 잠금 화면을 해제하며 검사 중에는 화면이 꺼지지 않게 합니다. 종료 시 원래 글자 크기·비행기 모드·키보드·화면 시간 제한·충전 중 화면 유지 설정을 복구합니다. 사용 중인 개발용 자료가 있으면 먼저 별도 보관하세요. 테스트 실행 중 에뮬레이터를 직접 조작하거나 다른 앱을 실행하지 않습니다.

## 검증 범위

수동 테스트를 위해 에뮬레이터를 열 때는 샘플 데이터도 함께 준비합니다. `-PcaptureScreenshots`로 앱·androidTest APK를 빌드하고 설치한 뒤 `com.poyal.perilog.capture.PreviewSamples#load`만 실행합니다. 물품 6개, 구성 2개, 입고 재고와 지난 2주·오늘·추가투석 예시를 추가하며 기존 입력과 수정값은 보존합니다. 준비가 끝나면 앱을 다시 열어 둡니다. 전체 E2E 실행은 별도의 검증 작업입니다.

- 기계투석 입력 → 계산된 900mL 확인 → 저장 → 재고 10→8EA → 추가투석 → 6EA
- 품목 추가, 일괄 입고, 입고 수정·취소, 사용기한 기본 무관
- 구성 수량 지우기·직접 재입력, 이번 기록만 수량 변경, 재저장 시 중복 차감 방지
- 품목 색 변경이 구성 목록·편집·선택·선택된 칩에 반영됨
- 품목·구성·입고의 미저장 입력 복구, 변경 버리기 확인, 저장 전 재고 불변
- 어제 기록 이어 쓰기, 아침 입력의 선택 날짜 유지, 미래 날짜 차단
- 소수점 이어 입력하기, 전체 지우기, 정수 수량 오류와 복구
- 제수량의 ? 도움말에서 계산 기준 변경·복원 시 계산값 반영
- 기간별 기록 표: 7D/30D/직접 기간, 여러 종류·원래 단위, 행 열기 후 필터 유지
- 통계의 기간·표 선택 유지, 치료 초안 재생성·회전, 실제 키보드와 큰 글씨에서 입력칸 전체 표시, 설정 입력 복구·다크 모드
- 백업은 실제 파일 I/O와 앱 백업 서비스를 통해 내보내기·복원·보호 파일·손상 파일 거절을 검증함

백업 시스템 파일 선택기, 생체 인증, One UI의 절전 알림은 이 E2E에서 자동 조작하지 않습니다. Galaxy Z 플립7에서 별도로 확인합니다. Activity 재생성 검사는 강제 종료·프로세스 사망 검사를 대신하지 않으며 별도 검증 결과는 검증 기록에 남깁니다.

## 결과 및 CI

각 실행의 HTML/XML 결과, 전체 실행 로그, Android 로그와 실패 화면은 `.tools/e2e-results/normal/` 또는 `large/`에 저장됩니다. Git에는 예시 사용자 자료를 담은 보고서를 커밋하지 않습니다.

`.github/workflows/test.yml`은 PR·main 푸시·수동 실행에서 단위 검사·Lint·API 35 에뮬레이터의 두 가지 E2E를 실행하고 보고서를 14일 보관합니다. README·readme·design 파일만 바뀐 main 푸시는 중복 실행하지 않습니다. 로컬 검증 장치는 API 36입니다. 이 작업은 APK를 배포하거나 릴리즈를 게시하지 않습니다. 프로세스 복구 스크립트는 새 품목 폼에 임시 이름을 입력하고 홈으로 이동합니다. Activity가 `STOPPED`이며 Android가 상태를 저장한 것을 확인한 뒤, 개발용 앱 UID의 `run-as`로 해당 PID만 종료합니다. 기존 PID가 사라질 때까지 제한 시간 안에서 확인합니다. 새 PID에서 미저장 내용을 확인한 뒤 버리므로 품목을 저장하지 않습니다. 결과는 `.tools/e2e-results/process/`에 남습니다.

GitHub에서의 실제 실행 여부는 로컬 통과와 구분해 기록합니다. Android 15의 창 재생성 오류를 확인하기 위해 로컬에도 API 35 / Pixel 7 에뮬레이터를 추가했습니다. CI와 같은 API에서 수정 전 실패를 재현하고, API 35·36의 기본/큰 글씨에서 수정 후 회전 검사를 확인합니다.

CI는 Android SDK 도구를 설치한 뒤 플랫폼·시스템 이미지 설치, AVD 생성, 부팅을 나눠 실행합니다. `ANDROID_USER_HOME`·`ANDROID_EMULATOR_HOME`·`ANDROID_AVD_HOME`을 러너 임시 디렉터리로 통일해 생성한 기기를 같은 경로에서 찾습니다. 부팅은 180초로 제한하고, 프로세스가 종료되면 즉시 실패합니다. 실패 로그 마지막 30줄은 Actions 요약에도 남깁니다. 환경변수 역할은 [Android 공식 문서](https://developer.android.com/tools/variables)를 참고하세요.

`am kill`은 Android가 종료해도 된다고 판단하는 프로세스만 대상으로 하므로 호출 직후 종료를 보장하지 않습니다([공식 설명](https://developer.android.com/tools/adb#am)). 복구 검사는 저장된 task를 유지한 실제 프로세스 사망을 재현하며, 종료 성공·새 PID·미저장 입력 복구를 각각 확인합니다. 실패 시 고정된 단계 이름과 오류 종류만 CI annotation에 표시하고, 화면 자료 수집 실패가 원래 오류를 덮지 않도록 했습니다.

회전·키보드 검사는 Activity 창 포커스를 기다리고 화면 키보드를 요청한 뒤 실제 IME inset 표시를 확인합니다. 회전·키보드 같은 OS 전환은 최대 30초 동안 기다리며 조건 이름과 화면 켜짐·잠금·창 포커스 여부를 오류에 남깁니다. 입력칸의 95% 이상 표시, 저장 버튼, 61.5 값 보존과 재고 불변을 계속 검사합니다. 회전 후 원래 방향 설정도 복구합니다.

CI는 긴 빌드 중에도 화면이 잠들지 않도록 부팅 직후 화면 유지 설정을 적용합니다. E2E와 프로세스 복구 스크립트도 각자 화면을 깨우고 잠금을 해제하므로 단독 실행이 가능합니다. 프로세스 복구 스크립트는 종료 시 원래 화면 설정을 복구합니다. 비밀번호가 설정되지 않은 개발용 에뮬레이터를 사용합니다.

특정 검사만 재현할 때는 전체 결과를 덮지 않도록 별도 결과 폴더를 지정할 수 있습니다. 기본 실행과 CI는 전체 15개 검사를 실행합니다.

```sh
PERILOG_TEST_TARGET='com.poyal.perilog.AppFlowTest#typedDraftSurvivesActivityRecreationRotationAndDoesNotConsumeStock' \
PERILOG_RESULTS_DIR="$PWD/.tools/e2e-ime-check" \
./scripts/test-e2e.sh all
```

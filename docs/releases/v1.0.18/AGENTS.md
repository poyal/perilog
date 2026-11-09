# v1.0.18 최종 검증 요약

## 판정과 출처

**게시 검증 완료.** [v1.0.18 릴리즈](https://github.com/poyal/perilog/releases/tag/v1.0.18)를 공개했고 APK·화면 ZIP·체크섬 세 파일을 공개 URL에서 다시 내려받아 검증한 원본과 바이트·해시·서명이 일치함을 확인했습니다.

버전 **1.0.18 · 코드 19 · 앱 ID `com.poyal.perilog` · 최소 API 30 · 대상 API 36 · DB 스키마 9**입니다. 어제·오늘·날짜 선택 시 해당 종류의 저장 기록/기존 초안을 조회하고, 미저장 변경의 저장·버리기·계속 입력과 같은 날 복수 기록 선택을 포함한 후보를 검증했습니다.

- `buildCommit`: `d84cc7f5a84837fa7212b99eed26dfef3f33ea0e`. 빌드 시작 시 깨끗한 작업 트리.
- 최종 소스에서 허용하는 후속 변경은 `docs/releases/v1.0.18/AGENTS.md`의 검증 결과뿐입니다. 앱·Gradle·검사·게시 스크립트·캡처 Kotlin은 빌드 이후 수정하지 않았습니다.
- `sourceCommit`: `d06904aa3f7ed6233afb3f59afd19cee243a7d3c`. 빌드 이후 차이는 위 검증 문서 1개뿐입니다.
- `releaseCommit`: `3354cfd6019e0c68147f15f47853b1eb8cd40d0b`. 단일 부모는 위 sourceCommit이며 `.release/`의 해당 버전 세 파일 추가만 포함합니다.
- annotated `v1.0.18`의 역참조와 원격 태그가 위 releaseCommit과 일치합니다. 게시 확인만 main 후속 문서 커밋에 남기며 태그·배포 파일은 이동하거나 교체하지 않습니다.

실행 ID `20261109T010911Z-v1.0.18-d84cc7f`, 작업 폴더 `/Users/poyal/Workspace/personal/perilog`, 게시 담당은 이번 Codex 세션입니다. 검증 시작 `2026-11-09T01:09:11.638309+00:00`, 완료 `2026-11-09T01:58:54.601808+00:00`, 전체 경과 2982.96초입니다. 사용자 자료 대신 검사 전용 AVD와 가상 자료만 사용했습니다.

호스트 macOS 27.2 arm64, Temurin JDK 17.0.14, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, compile SDK 36, Build Tools 36.0.0, Android Emulator 37.1.11입니다. 의존성·최소 SDK·지원 OS 범위는 변경하지 않았습니다.

| Android 환경 | 수행 범위 |
| --- | --- |
| `perilog_compat_api35` / `emulator-5590` / arm64-v8a / 4KB | 1080×2340px·440dpi, 글씨 1.0·1.5. 전체 UI, 실제 프로세스 복구, 서명 신규 설치·업데이트 |
| `perilog_compat_api36` / `emulator-5592` / arm64-v8a / 4KB | 1080×2340px·440dpi, 글씨 1.0·1.5. 지정 입력·키보드·회전·날짜/시간 회귀 |

## 실제 검사 결과

아래 최종 명령 종료 코드는 모두 0입니다. 소스의 테스트 메서드·SDK별 예상 실행 목록을 단위/UI XML과 대조했습니다.

| 검사 | 결과 |
| --- | --- |
| 전체 단위·저장소 | 253/253 통과, 실패·오류·생략·누락 0. Room API 30·31·35 포함 |
| clean 빌드·서명 | Debug·AndroidTest·Release 성공, 165.56초 |
| Debug·Release Lint | 오류 0, 경고 각각 35·36개 |
| API 35 normal | 112/112 통과 · 실패/오류/생략/누락 0 · XML 865.130초 |
| API 35 large | 112/112 통과 · 실패/오류/생략/누락 0 · XML 844.787초 |
| API 36 normal | 11/11 통과 · 실패/오류/생략/누락 0 · XML 119.264초 |
| API 36 large | 11/11 통과 · 실패/오류/생략/누락 0 · XML 113.351초 |
| 실제 프로세스 복구 | `PASS: process 14602 -> 14933; unsaved product form restored, then discarded.` |
| 서명 APK 신규 설치 | 실제 SAF 가져오기·설정·재고·재시작 및 전체 데이터 보존 통과 |
| 서명 APK 실제 기록 저장 | UI로 62.3→62.4kg 수정·저장·재시작, 원래 ID/날짜·62,400g·saved=1·연결 사용 1행·재고 13EA 확인 |
| 공개 1.0.17 → 최종 후보 덮어 설치 | 삭제/초기화 없이 `install -r`, 19개 테이블·27행·256개 기존 필드와 모든 ID 유지 |
| 백업·권한 | 재고 화면·전체 JSON·DataStore·보호 백업·알림·SAF 유지. 기존 백업 바이트 유지와 같은 폴더에 새 백업 확인 |
| 설치 파일 일치 | 신규·업데이트의 설치된 base.apk가 고정 APK와 바이트 일치 |
| APK 정렬 | ZIP와 64비트 라이브러리 4개 16KB 정적 정렬 통과. 실제 16KB OS는 별도 범위 |
| 검사 스크립트 | Python unittest 22개 통과 |
| 문서·화면 | 로컬 링크 457개, 버튼 기준 60개 소스, 앱 안내 PNG 55장·10,318,769바이트 원본 일치, 화면 ZIP 95장 원본·CRC 일치 |

API 35 전체 UI 구성: `AppFlowTest` 46 · `AppointmentFlowTest` 12 · `CalendarFlowTest` 3 · `UiComponentsTest` 2 · `TemporalControlsTest` 6 · `TopSnackbarHostTest` 4 · `ReplenishmentFlowTest` 7 · `StockForecastFlowTest` 3 · `WidgetFlowTest` 13 · `SettingsFlowTest` 8 · `PrivacyFlowTest` 3 · `UpdatesFlowTest` 2 · `UsabilityFlowTest` 3. 직전 버전의 프로필별 104개에서 날짜별 조회·입력 보호 관련 8개가 추가되어 112개입니다. 단위 검사는 날짜/유형 분리·저장 기록 우선·정렬 안정성 3개가 추가되어 250→253개입니다.

날짜 이동 확인창과 기록 선택창은 기본/1.5배 글씨 × 밝게/어둡게의 실제 캡처 8장을 직접 확인했습니다. 버튼 겹침·문구 잘림이 없고 긴 문구는 줄바꿈됐습니다.

API 36은 실행 전에 지정한 11개 회귀입니다. `AppFlowTest`의 `typedDraftSurvivesActivityRecreationRotationAndDoesNotConsumeStock`, `dwellInputAndUnsavedDialogRemainUsableInLandscapeAndBothThemes`, `rangePickerDirectInputRejectsReversedDatesAndWorksInDarkLandscape`, `SettingsFlowTest`의 `timeDialogConfirmationAndSaveRemainReachableInBothOrientations`, `saveRemainsReachableInLandscapeAndInputsArePreserved`, `TemporalControlsTest` 전체 6개를 실행했습니다. 실제 키보드·회전·입력 보존·밝게/어둡게·저장/확인 버튼을 확인했으며 API 36 전체 UI 통과로 표현하지 않습니다.

Debug Lint 경고: `InlinedApi` 2 · `UnusedAttribute` 9 · `AppBundleLocaleChanges` 1 · `ModifierParameter` 1 · `UseCompatTextViewDrawableXml` 6 · `ObsoleteSdkInt` 1 · `NestedWeights` 6 · `SmallSp` 7 · `UseKtx` 2. Release는 `MonochromeLauncherIcon` 1개가 추가됩니다. 오류는 없습니다.

## 실행 명령과 데이터 보존

```sh
./scripts/build.sh clean :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease -PcaptureScreenshots --offline --max-workers=2 --console=plain
env -u PERILOG_TEST_TARGET PERILOG_SERIAL=emulator-5590 PERILOG_RESULTS_DIR=<실행폴더>/api35-e2e ./scripts/test-e2e.sh all
PERILOG_SERIAL=emulator-5590 PERILOG_PROCESS_RESULTS_DIR=<실행폴더>/process python3 scripts/test-process-restore.py
PERILOG_SERIAL=emulator-5592 PERILOG_TEST_TARGET=<위에 명시한 11개 검사 대상> PERILOG_RESULTS_DIR=<실행폴더>/api36-e2e ./scripts/test-e2e.sh all
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check-ui-buttons.py
python3 scripts/sync-guide-assets.py --check
python3 scripts/check-apk-pages.py dist/perilog-1.0.18.apk --output <실행폴더>/apk-pages.json
```

API 35 전체 명령 1734.30초, API 36 지정 회귀 명령 277.35초입니다.

최초 보조 `test-compatibility.py --suite full --profile all`은 캘린더 권한 사전 준비에서 멈췄습니다. OS가 `No operations. Default mode: allow`를 반환했는데 실행기가 명시적 `READ_CALENDAR: allow` 문자열만 허용한 것이 원인입니다. 이 실행은 테스트 0개로 통과 처리하지 않았습니다. 앱·검사 소스나 후보 APK를 바꾸지 않고 운영 문서의 `test-e2e.sh all`로 기본·큰 글씨 전체 13개 클래스를 실행했으며, 캘린더 3개도 각 프로필에서 포함해 전부 통과했습니다. 실패한 전체 검사 대신 일부 범위로 대체하지 않았습니다.

최초 신규 설치 자료는 취소된 사용을 사용 확정 상태로 연결한 가상 자료 오류로 가져오기가 거부됐습니다. `compat-ml`의 확정 상태를 바로잡고 모든 확정 사용의 연결을 검사한 뒤 신규 설치 전체를 새로 실행했습니다. 실패 기록은 `signed-fresh-fixture-rejected`로 구분했으며 앱·배포 APK는 변경하지 않았습니다.

서명 검사는 공식 `scripts/test-compatibility-system.py`에 이번 실행의 로컬 `release-system-check.py`로 가상 자료·전후 재고 화면 비교를 추가했습니다. 신규 설치는 `--serial emulator-5590 --avd perilog_compat_api35 --apk dist/perilog-1.0.18.apk --reset-fixture --output <실행폴더>/signed-fresh`, 업데이트는 같은 인자에 `--previous-apk <공개 1.0.17 APK> --output <실행폴더>/signed-upgrade`를 사용했습니다. reset은 각 시나리오 시작 전 검사 AVD의 가상 자료에만 적용했고 구버전→후보 사이에는 삭제·초기화하지 않았습니다. 실제 수정·저장은 로컬 `release-save-check.py`로 확인했습니다. 이 보조 파일과 순차 실행·결과 대조용 파일은 저장소의 공통 도구가 아닙니다.

실제 Android 파일 선택기로 가져온 필수 9개 테이블: `products` 4 · `templates` 1 · `treatments` 5 · `usages` 3 · `receipts` 3 · `counts` 3 · `audit` 1 · `drafts` 2 · `settings` 1. 기계·추가투석, 음수·0·미입력, mL·g·kg, 과거 계산 기준, 취소된 입고/사용, 삭제 기록의 사용 내역, 비활성 품목, 신규/수정 초안, 비기본 설정, 기존 범위 초과 평균저류시간·미완성 시각·예약별 시각을 포함했습니다.

DB는 앱을 멈추고 WAL과 함께 수집하여 행 수뿐 아니라 ID와 기존 필드 전체를 비교했습니다. 스키마 9·무결성 `ok`, UID `10211`, 최초 설치 `2026-11-09 10:50:45`가 유지됐으며 의도한 데이터 변환도 없습니다. 재고 UI의 모든 앱 텍스트와 수량을 전후 비교했습니다.

자동·수동 전체 내보내기 JSON은 `exportedAt`만 제외하고 일치했습니다. DataStore는 조회 메타데이터 `appUpdate`만 제외하고 기존 값을 비교했습니다. 알림 거부→허용→취소→재허용, SAF persisted=0x3, 보호 백업의 기존 바이트와 폴더 재선택 없는 새 백업을 확인했습니다. 연결 폴더의 백업은 1→2개로 늘었고 기존 파일의 SHA-256은 유지됐습니다. 통합 릴리즈 검증/잠금/기록 자동 차단은 미구현이며 각 결과와 필수 조건을 직접 대조했습니다.

## 고정 산출물

| 파일 | 바이트 | SHA-256 |
| --- | ---: | --- |
| `perilog-1.0.18.apk` | 20,495,753 | `54e7a2011c657603bd96ebae33bbd4737f6903469530fc86aac96f666df46776` |
| `perilog-1.0.18-screenshots.zip` | 15,959,607 | `4933e389be135225a59ed39be18bbd86097dafeb3f027c0275e7ea315ed9bf6e` |
| `perilog-1.0.18.sha256` | 182 | `187eec9d271f38806ae44e6145b7ba61ccf151bcdbaf6825728d4590cca2c9af` |

빌드 직후 고정한 `dist/perilog-1.0.18.apk`를 신규 설치·업데이트·배포에 사용합니다. [직전 공개 APK](https://github.com/poyal/perilog/releases/download/v1.0.17/perilog-1.0.17.apk)는 20,032,083바이트, SHA-256 `b396460850b323bd84863535370781765f91214b0560d889d454acf1aab8719e`이며 공개 체크섬·GitHub digest와 일치했습니다. 두 버전 인증서 SHA-256은 `79adbeead4f7fbc2a5bf99219ee2fdd909f14eb18817c2b4f6139edbce0937dd`로 같습니다. 배포 파일에 키·비밀번호·키 백업·검사 DB는 포함하지 않았습니다.

ZIP은 현재 사용자 매뉴얼·화면 안내에서 참조한 95개 번호 PNG만 포함합니다. 참조되지 않는 과거 화면 `11-statistics-table`, `57-record-widget`, `58-appointments-widget`, `61-dark-widget`은 제외했습니다. 체크섬은 APK와 ZIP 두 줄입니다. 변경 화면 97·98·99와 버전 화면 25·26·29는 이번 API 35 준비 빌드로 촬영했으며 촬영 조건·기존 화면 재사용은 [화면 안내](../../화면-안내.md)에 적었습니다.

## 공개 게시 확인

[게시 Actions](https://github.com/poyal/perilog/actions/runs/42548512456)는 `2026-11-09T02:00:26Z`에 시작해 `2026-11-09T02:00:48Z`에 성공으로 끝났습니다(22초). APK 재빌드 없이 고정한 세 파일만 게시했습니다. 태그 푸시 시작부터 공개 파일 재검증 완료까지 81.58초입니다. `main`과 `v1.0.18`만 atomic push했으며 `release/v1.0.18` 브랜치는 중복 푸시하지 않았습니다.

공개 시각 `2026-11-09T02:00:44Z`, 공개 파일 확인 시각 `2026-11-09T02:01:35.621095+00:00`입니다. 최신 안정 릴리즈이며 초안/사전 공개가 아니고 첨부 파일은 정확히 세 개입니다. 세 파일의 GitHub digest·공개 다운로드·로컬 원본이 같고, 공개 APK의 기존 인증서·앱 ID·버전 1.0.18/코드 19·최소/대상 API 30/36 및 ZIP 95장의 원본 바이트·CRC를 다시 확인했습니다. GitHub가 생성한 릴리즈 본문(Full Changelog 연결)을 검토했으며 패치노트 파일은 저장소에 추가하지 않았습니다.

원격 main·동일 버전 태그·공개 릴리즈·진행 중인 게시 실행을 확인했습니다. 공개 API에서 보이지 않는 초안/기존 자산은 인증된 게시 스크립트가 해시·다운로드 바이트를 대조합니다. 해당 배포 커밋에는 게시 실행 1개만 생성됐고, main·태그 푸시로 무거운 Android 검증이 자동 실행되지 않았음을 확인했습니다. `.github/workflows/test.yml`은 수동 진단 전용으로 유지됩니다.

게시 근거는 `publication-preflight.json`, `commit-relationship.json`, `publish-push-command.json`, `publication-run.json`, `public-release.json`, `public-verification.json`, 공개 APK 서명·매니페스트 출력입니다. 공개 파일 검증 JSON의 SHA-256은 `d8211f51440f8e68c3d2f3f3aaf66e14342c37e14bdcab62f6ac28b1b06a01c1`입니다.

## 실행 근거와 별도 확인 범위

실행 당시 `.tools/release-checks/20261109T010911Z-v1.0.18-d84cc7f/`의 XML·JSON·서명·설치/업데이트 스냅샷을 직접 확인했습니다. 최종 사실은 이 문서에 남기고 정리 규칙에 따라 중간 로그·검사 DB·APK·재다운로드·임시 worktree를 작업 종료 후 삭제합니다.

| 실행 근거 | SHA-256 |
| --- | --- |
| `unit-inventory.json` | `e22aabf57b7444344a0adac3621ad5321f90d2f4a82ceb7a35a666b6ba16c232` |
| `api35-verification.json` | `fd61618ccb4f186ad995bd5824bc8fc9f58941e273f575976d0de6f5a1f1bd33` |
| `api36-verification.json` | `2ef2c125d39c1ecd0fdb54b92546e1ef44d5bcd398ade97890ba4aae3c83e1d5` |
| `process/result.txt` | `45762c48127991ec004eef37655b4cf93e196f428c9f2220aafcb41b5060bf57` |
| `signed-fresh/result.json` | `279e3760a4012fc340c46f49a7f5b461fbf6f59945b08281ee48adaec4cc6081` |
| `signed-record-save/result.json` | `b8a4e2d3a050a887b26a61ca95ddde6ded30d21a41db4246cddaccde90ed2ac5` |
| `signed-upgrade/result.json` | `06462b293263c769e635c5378e56b5c695457d5b22d60e972c31a7f9f3864075` |
| `artifacts.json` | `91da6beaadcfab25e119dc0da3803efa578815f9d36978ce21daa6ad0f398d33` |
| `date-dialog-visual-review.json` | `b6b241fbb456f7340cce7124a92e53732768d0733a4a36dcbe1479d82e59f19d` |
| `release-system-check.py` | `91c8f924f0df199ea0fade5efbde373b79f171e23b57c9b8bd732fa3a84357c1` |
| `release-save-check.py` | `4b44bbe99a2a30ff626fc1f52819608058166e23008fa784fd3229b86954fa2e` |

API 30·31 실제 서명 설치는 최소 SDK·OS 호환 동작이 바뀌지 않아 반복하지 않았습니다. Room API 30·31·35 단위 검사는 이번 후보로 수행했습니다. Galaxy Z 플립7 실물 접기/펼치기, 삼성 키보드·파일 선택기·생체 인증·장시간 절전, 다른 OS/ABI 실기기 및 실제 16KB OS는 미검증 범위입니다. 에뮬레이터 통과로 이를 대신하지 않습니다. 무거운 원격 Android 진단은 불필요하여 실행하지 않았고 수동 전용 설정을 유지했습니다.

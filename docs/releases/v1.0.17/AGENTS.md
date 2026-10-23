# v1.0.17 최종 검증 결과

[사용자 매뉴얼](README.md) · [GitHub Release](https://github.com/poyal/perilog/releases/tag/v1.0.17) · [공통 개발 규칙](../../development.md) · [배포 절차](../../release.md)

상태: **게시·공개 파일 검증 완료**. 2026-10-23에 이번 후보로 실행한 결과입니다. 이전 버전의 통과 결과를 대신 사용하지 않았습니다.

## 게시 결과

[v1.0.17 공개 릴리즈](https://github.com/poyal/perilog/releases/tag/v1.0.17)와 [게시 Actions](https://github.com/poyal/perilog/actions/runs/39908730239)를 확인했습니다. 공개 시각 `2026-10-23T05:58:46Z`, 공개 파일 확인 완료 `2026-10-23T05:59:42.439824+00:00`이며, 태그 푸시 시작부터 재다운로드 확인까지 87.1초입니다. 게시 워크플로 시작부터 완료 상태 갱신까지 22.0초입니다.

`main`과 annotated `v1.0.17` 태그를 원자적으로 푸시했고 `release/v1.0.17` 브랜치는 원격에 푸시하지 않았습니다. 기존 게시 워크플로가 재빌드 없이 검증한 세 파일을 게시했습니다. 최신 정식 릴리즈가 v1.0.17이며 초안·사전 공개가 아님을 확인했습니다.

공개 APK·화면 ZIP·체크섬을 모두 다시 내려받아 크기·SHA-256·GitHub digest·고정 원본 바이트를 비교했습니다. APK 앱 ID·버전·코드·최소/대상 API·기존 인증서, ZIP의 PNG 92장 전체 바이트·CRC, 체크섬 정확히 두 항목을 재검증했습니다. 원격 annotated 태그 역참조가 배포 커밋과 같고, 원격 Android 검증 워크플로가 수동 전용이며 이번 main·태그 푸시로 자동 Android 검사 실행이 생기지 않았음을 확인했습니다.

공개 API·원격 Git·Actions에서 동일 버전과 동시 게시를 확인했습니다. 공개 API로 볼 수 없는 초안·기존 자산은 인증된 게시 스크립트가 해시와 재다운로드 바이트를 검사합니다. GitHub가 생성한 릴리즈 본문을 확인했습니다. 게시 근거는 `publication-preflight.json`, `publish-push-command.json`, `publication-run.json`, `public-release.json`, `public-verification.json`, 공개 APK 서명·매니페스트 출력입니다. 공개 파일 검증 JSON의 SHA-256은 `e11eaa0d8ad0c5c6466ae49c8ff8ecd848fff6670d279676c133fc6236c66220`입니다.

## 소스·환경

버전 **1.0.17 · 코드 18 · 앱 ID `com.poyal.perilog` · 최소 API 30 · 대상 API 36 · DB 스키마 9**입니다. 날짜·시각·평균저류시간의 라벨과 선택 버튼, 선택 대화상자, 사용자 안내·화면을 반영한 후보를 검증했습니다.

- `buildCommit`: `ef0785788559873344f30923203e2f1881da7a40`. 빌드 시작 시 깨끗한 작업 트리.
- 최종 소스의 허용 변경: `docs/releases/v1.0.17/AGENTS.md` 한 파일에 이번 검증 결과만 추가. 앱·Gradle·검사·게시 스크립트·캡처 Kotlin 변경 없음.
- `sourceCommit`: `e624ba6af8f89e615916a60cee3d2ea2c27d4bc7`.
- `releaseCommit`: `7c38cacf8260ec7b8c821a473b6e7489f64ef346`. 단일 부모가 위 소스 커밋이며, 차이는 `.release/perilog-1.0.17.apk`, `.release/perilog-1.0.17-screenshots.zip`, `.release/perilog-1.0.17.sha256` 세 파일 추가뿐입니다.
- annotated `v1.0.17`의 역참조가 위 배포 커밋과 일치합니다. 게시 확인 문서만 main의 후속 커밋으로 남기며 태그·배포 파일은 이동하거나 교체하지 않습니다.

실행 ID `20261023T052521Z-v1.0.17-ef07857`, 작업 폴더 `/Users/poyal/Workspace/personal/perilog`, 게시 담당은 이번 Codex 세션입니다. 최종 후보 검증 시작 `2026-10-23T05:25:21.876500+00:00`, 완료 `2026-10-23T05:56:35.485454+00:00`, 소요 1873.6초입니다. 앞선 실패 후보의 실행 ID는 `20261023T043407Z-v1.0.17-db6ec61`, `20261023T044845Z-v1.0.17-6ebc14b`이며 최종 통과 결과와 구분했습니다. 첫 후보 빌드 시작 `2026-10-23T04:34:10.243563+00:00`부터 최종 검증 완료까지는 4945.2초입니다. 검사 전용 에뮬레이터와 가상 자료만 사용했습니다.

호스트 macOS 27.0.1 arm64, Temurin JDK 17.0.14, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, compile SDK 36, Build Tools 36.0.0입니다. 이번에 의존성·최소 SDK·OS 호환 범위는 변경하지 않았습니다. 의존성 잠금 파일로 재현했다고 주장하지 않습니다.

| Android 환경 | 실행 범위 |
| --- | --- |
| `perilog_compat_api35` / `emulator-5590` / API 35 / arm64-v8a / 4KB | 1080×2340px·440dpi, 글씨 1.0·1.5. 전체 UI, 실제 프로세스 복구, 최종 서명 APK 신규 설치·업데이트 |
| `perilog_compat_api36` / `emulator-5592` / API 36 / arm64-v8a / 4KB | 1080×2340px·440dpi, 글씨 1.0·1.5. 입력·키보드·회전·날짜/시간 추가 회귀 |

## 필수 검사 결과

아래 명령 종료 코드는 모두 0입니다. 단위·UI XML의 예상 목록과 실제 실행 목록을 대조했고 실패·오류·생략·누락은 모두 0입니다.

| 검사 | 실제 결과 |
| --- | --- |
| 전체 단위·저장소 | 250개 통과. Room API 30·31·35 포함. 테스트 메서드와 SDK별 실행 이름 일치 |
| clean 빌드·서명 | Debug·AndroidTest·Release 성공. 기존 키로 서명, 122.53초 |
| Debug·Release Lint | 오류 0, 경고 각각 35·36개 |
| API 35 전체 UI | 13개 클래스, 기본 104개·큰 글씨 104개 통과. XML 시간 535.796초·547.295초, 전체 명령 1108.01초 |
| API 36 지정 회귀 | 기본 11개·큰 글씨 11개 통과. XML 시간 87.351초·88.857초, 전체 명령 199.72초 |
| 실제 프로세스 복구 | `PASS: process 10880 -> 11659; unsaved product form restored, then discarded.` |
| 서명 APK 신규 설치 | 실제 파일 가져오기·재고·설정 진입·재시작 보존. 기록을 실제 UI로 62.3→62.4kg 수정·저장하고 재시작 후 UI와 DB 62,400g·saved=1 확인 |
| 공개 1.0.16 → 최종 후보 덮어 설치 | 삭제 없이 `install -r`. 19개 테이블·30행·274개 기존 필드와 모든 ID 보존, DB 무결성 ok |
| 백업·권한 | UID·최초 설치 시각·재고 화면·전체 JSON·초안·DataStore·보호 백업·알림·SAF 폴더 권한 유지. 기존 폴더에서 백업 1→2개, 이전 파일 바이트 유지 |
| 설치 파일 일치 | 신규·업데이트 검사에서 설치된 base.apk가 고정 APK와 일치 |
| APK 정렬 | ZIP와 64비트 라이브러리 4개의 16KB 정적 정렬 통과. 실제 16KB OS 검사는 별도 |
| 검사 스크립트 회귀 | 22개 통과. 게시·호환성 결과 판정·UI 대상·ELF 정렬 검사 포함 |
| 문서·화면 | 로컬 링크 465개, 공통 버튼 기준 60개 소스, 앱 안내 PNG 52장·9,871,965바이트 원본 일치, 화면 ZIP 92장 원본·CRC 일치 |

전체 UI 구성은 `AppFlowTest` 38, `AppointmentFlowTest` 12, `CalendarFlowTest` 3, `UiComponentsTest` 2, `TemporalControlsTest` 6, `TopSnackbarHostTest` 4, `ReplenishmentFlowTest` 7, `StockForecastFlowTest` 3, `WidgetFlowTest` 13, `SettingsFlowTest` 8, `PrivacyFlowTest` 3, `UpdatesFlowTest` 2, `UsabilityFlowTest` 3입니다. 직전 릴리즈의 프로필별 101개에서 104개가 됐습니다. 기존 날짜/시간 공통 컴포넌트 검사 3개를 별도 `TemporalControlsTest`의 6개로 보강해 순증 3개입니다.

API 36은 실행 전에 지정한 범위입니다. `AppFlowTest`의 `typedDraftSurvivesActivityRecreationRotationAndDoesNotConsumeStock`, `dwellInputAndUnsavedDialogRemainUsableInLandscapeAndBothThemes`, `rangePickerDirectInputRejectsReversedDatesAndWorksInDarkLandscape`, `SettingsFlowTest`의 `timeDialogConfirmationAndSaveRemainReachableInBothOrientations`, `saveRemainsReachableInLandscapeAndInputsArePreserved`, `TemporalControlsTest` 전체 6개를 실행했습니다. 실제 키보드, 입력값 보존, 회전, 밝게·어둡게, 저장·확인 버튼 접근성을 포함하며 API 36 전체 UI 통과로 표현하지 않습니다.

Debug Lint 경고는 `InlinedApi` 2 · `UnusedAttribute` 9 · `AppBundleLocaleChanges` 1 · `ModifierParameter` 1 · `UseCompatTextViewDrawableXml` 6 · `ObsoleteSdkInt` 1 · `NestedWeights` 6 · `SmallSp` 7 · `UseKtx` 2입니다. Release는 여기에 `MonochromeLauncherIcon` 1개가 추가됩니다.

## 실행 명령과 데이터 비교

```sh
./scripts/build.sh clean :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease -PcaptureScreenshots --offline --max-workers=2 --console=plain
env -u PERILOG_TEST_TARGET PERILOG_SERIAL=emulator-5590 PERILOG_RESULTS_DIR=<실행폴더>/api35 ./scripts/test-e2e.sh all
PERILOG_SERIAL=emulator-5590 PERILOG_PROCESS_RESULTS_DIR=<실행폴더>/process python3 scripts/test-process-restore.py
PERILOG_SERIAL=emulator-5592 PERILOG_TEST_TARGET=<위에 명시한 11개 검사 대상> PERILOG_RESULTS_DIR=<실행폴더>/api36 ./scripts/test-e2e.sh all
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check-ui-buttons.py
python3 scripts/sync-guide-assets.py --check
python3 scripts/check-apk-pages.py dist/perilog-1.0.17.apk --output <실행폴더>/apk-pages.json
```

서명 설치·업데이트는 공식 `scripts/test-compatibility-system.py`를 실행하는 이번 세션의 로컬 `release-system-check.py`에서 가상 자료와 전후 재고 화면 비교만 보강했습니다. `--serial emulator-5590 --avd perilog_compat_api35 --apk dist/perilog-1.0.17.apk --reset-fixture --output <실행폴더>/signed-fresh`로 신규 검사를 실행했고, 업데이트 검사는 `--previous-apk <공개 1.0.16 APK> --output <실행폴더>/signed-upgrade --reset-fixture`를 추가했습니다. reset은 각 새 설치 시나리오 시작 전에 이 검사 전용 AVD의 이전 가상 자료를 제거하는 데만 적용했고, 공개 구버전→후보 설치 사이에는 삭제·초기화하지 않았습니다. 실제 기록 수정·저장은 로컬 `release-save-check.py`로 확인했습니다. 두 실행용 보조 파일은 새 체크아웃에 제공되는 공통 명령이 아닙니다.

가상 자료는 실제 Android 파일 선택기로 가져왔습니다. 필수 9개 테이블의 행 수는 `products` 4 · `templates` 1 · `treatments` 5 · `usages` 3 · `receipts` 3 · `counts` 3 · `audit` 1 · `drafts` 2 · `settings` 1이며 모두 비어 있지 않습니다. 기계·추가투석, 음수·0·미입력, mL·g·kg, 과거 계산 기준, 취소 이력, 삭제 기록의 사용 내역, 비활성 품목, 미저장·수정 중 초안, 비기본 설정, 기존 범위 초과 평균저류시간, 미완성 시각, 병원 예약·진료과·치료 항목 시각을 포함했습니다. DB는 앱을 멈춘 뒤 WAL을 포함해 수집했습니다. 행 수뿐 아니라 전체 ID와 기존 필드를 비교했고 스키마 9를 유지했습니다.

전체 내보내기 JSON은 `exportedAt`만 제외하고 자동·수동 내보내기를 비교했습니다. DataStore는 업데이트 조회 메타데이터 `appUpdate`만 제외하고 기존 값을 비교했습니다. 실제 알림 거부→허용→취소→재허용, SAF persisted=0x3, 기존 백업 파일 보존과 폴더 재선택 없는 새 백업을 확인했습니다. 스크립트의 자동 판정과 실행 자료를 직접 대조했으며 통합 검증·기록 자동 차단·작업 폴더 잠금이 구현됐다고 기록하지 않습니다.

앞선 후보의 기본 글씨 전체 104개 중 치료 항목 시간 검사 1개가 예전 `시간 지정` 문구를 찾아 실패했습니다. 실제 화면의 `피검사 시간 선택` 접근성 설명으로 검사 대상을 수정하고 기본·큰 글씨 집중 검사를 통과한 뒤, 커밋 `ef0785788559873344f30923203e2f1881da7a40`에서 새로 clean 빌드·전체 필수 검사를 수행했습니다. 호환성 검사 목록의 예전 알림 시간 메서드 이름도 현재 이름으로 연결한 후 검사 스크립트 22개를 통과했습니다. 실패한 앞선 결과를 이번 전체 통과로 대체하지 않았습니다.

두 번째 후보 `6ebc14b6e674009bba81f131fe0347f31c75ba86`의 API 36 기본 회귀 11개 중 알림 시간 검사 1개가 키보드 종료 애니메이션 중의 임시 가로 배치를 저장 후 배치와 비교해 실패했습니다. 화면의 IME 높이가 0이고 앱 창 포커스·플랫폼 UI가 안정된 시점까지 기다린 뒤 위치를 비교하도록 검사만 수정했습니다. 같은 시나리오 기본·큰 글씨 집중 검사를 통과한 후 현재 커밋에서 clean 빌드와 API 36 지정 회귀 전체, API 35 전체, 서명 APK 신규·업데이트를 다시 실행했습니다. 앱의 화면 동작이나 저장 코드는 이 수정에서 바꾸지 않았습니다.

## 고정 산출물

| 파일 | 바이트 | SHA-256 |
| --- | ---: | --- |
| `perilog-1.0.17.apk` | 20,032,083 | `b396460850b323bd84863535370781765f91214b0560d889d454acf1aab8719e` |
| `perilog-1.0.17-screenshots.zip` | 15,538,292 | `d3e79f6ece1b200de1ea6ad06fdfa2dc6eff706163b84d1bd4c88efe8e1ffa78` |
| `perilog-1.0.17.sha256` | 182 | `a56ff0fe1f2d3be5fc369853d1f515793aad5a4e4f72a60ed58aab8dc0d73c3d` |

서명 APK를 빌드 직후 `dist/perilog-1.0.17.apk`로 고정했으며 신규 설치·업데이트에서 같은 바이트를 사용했고 게시할 파일도 같은 바이트로 고정했습니다. [직전 공개 APK](https://github.com/poyal/perilog/releases/download/v1.0.16/perilog-1.0.16.apk)는 20,068,015바이트, SHA-256 `eaf05eafba6833cbf365db50ed0c6b72485155631753e941227b73920bd2bb1a`이며 공개 체크섬·GitHub digest와 일치합니다. 두 APK의 인증서 SHA-256은 `79adbeead4f7fbc2a5bf99219ee2fdd909f14eb18817c2b4f6139edbce0937dd`로 같습니다. 배포 파일에 키·비밀번호·키 백업·검사 DB를 포함하지 않았습니다.

## 근거와 남은 범위

실행 당시 `.tools/release-checks/20261023T052521Z-v1.0.17-ef07857/`에서 XML·JSON·서명·설치/업데이트 스냅샷을 확인했습니다. 아래 해시는 그 실행 근거를 식별합니다. 정리 규칙에 따라 최종 사실은 이 문서에 보관하고 중간 로그·검사 DB·APK·재다운로드·임시 worktree는 종료 후 삭제합니다.

| 실행 근거 | SHA-256 |
| --- | --- |
| `unit-inventory.json` | `92158199832181d4d194c38c3e951b92993d0317695046b8ed7b3d161cfdaad0` |
| `api35-verification.json` | `df51fd4ed3e6662043399c616e682ec47c56958e74a25197a3d8236945cb5040` |
| `api36-verification.json` | `5257e0bb968262ba029e0377fdb592dbac2d8e6b14cd49da9aade88e54d277c0` |
| `process/result.txt` | `9eb190959624aa151479339fea3e14de77a5d123fd81f85dbf480172d7276b7d` |
| `signed-fresh/result.json` | `3e2cf35dd3064c765421fd9e18dbb7a12417cf565a4687439ad16a5f24d94cb8` |
| `signed-record-save/result.json` | `c425cd78e11353f7794e8f40e9142aa022a4c797fcff27ae12c4773e50906659` |
| `signed-upgrade/result.json` | `c547adb5f800fad63103d048e96ecd1d7d59862e71ef51528fdf34826472c0df` |
| `script-tests-verification.json` | `ff938fa37d90b1935165206b7bca19881ddce294aa53e86fa81cdf465bb7a68d` |
| `release-system-check.py` | `43d6039ad011f3aa6053a188cd381b4df73051ffe3a89a5ba878ce6912e6679a` |
| `release-save-check.py` | `57093e6b8b99d5293a1f0f78e8199dd886c3ca0d6dd1ec59d49ddc0eef8993d0` |

API 30·31 실제 서명 설치는 최소 SDK·OS 호환 동작이 바뀌지 않아 반복하지 않았습니다. Room API 30·31·35 단위 검사는 이번 후보로 수행했습니다. Galaxy Z 플립7 실물 접기·펼치기, 삼성 키보드·파일 선택기·생체 인증·장시간 절전, 다른 OS/ABI 실기기와 실제 16KB OS 동작은 미검증 범위입니다. API 35·36 에뮬레이터 통과로 이를 대신하지 않습니다. 원격 Android 진단은 불필요하여 실행하지 않았고 수동 전용 설정을 유지했습니다.

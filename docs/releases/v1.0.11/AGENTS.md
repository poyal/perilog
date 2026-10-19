# v1.0.11 최종 검증 결과

[사용자 매뉴얼](README.md) · [GitHub Release](https://github.com/poyal/perilog/releases/tag/v1.0.11) · [공통 개발 규칙](../../development.md) · [배포 절차](../../release.md)

- 공개 태그 커밋: `4270546f4338780726ef6f697b9034319b2a810a`
- 아래 결과는 기존 최종 검증 기록을 정리한 것이며 이번 문서 정리에서 재실행한 결과가 아닙니다. 작업 로그·중간 후보·로컬 설치 파일은 보관하지 않습니다.

## v1.0.11 — 2026-10-06 게시·2026-10-07 공개 파일 검증 완료

버전 `1.0.11` · 코드 `12` · 앱 ID `com.poyal.perilog` · 스키마 `8`. 저장 결과를 상태 표시줄 아래 상단 오버레이로 옮겨 본문·저장 버튼 위치를 유지하고, 백업·데이터 구역을 도움말 바로 위로 이동했습니다. README·사용 안내·앱 안내와 관련 실행 화면을 함께 갱신했습니다. 공개 파일도 같은 바이트로 검증했습니다.

[v1.0.11 공개 릴리즈](https://github.com/poyal/perilog/releases/tag/v1.0.11) 게시와 공개 파일 재검증을 완료했습니다. 빌드 커밋은 `1359113d848f2cebfdcb73907ce415d36cc6718b`, 최종 소스 커밋은 `84ebedb47ed4ec0b0802c5394fd69a237103be4f`, 배포·태그 커밋은 `4270546f4338780726ef6f697b9034319b2a810a`입니다. 빌드 후 차이는 `docs/releases/v1.0.11.md`와 `docs/releases/v1.0.16/AGENTS.md` 두 문서뿐입니다. 배포 커밋은 최종 소스의 단일 자식이며 `.release/`의 해당 버전 세 파일 추가만 포함합니다. 원격 annotated 태그도 같은 배포 커밋을 가리킵니다.

[게시 Actions](https://github.com/poyal/perilog/actions/runs/37467881685)는 재빌드 없이 검증한 파일을 게시했고 성공했습니다. 공개 시각은 `2026-10-06T13:04:10Z`, 공개 재검증 완료는 `2026-10-06T20:21:00.270742+00:00`입니다. 게시 시작부터 공개까지 0분 33초, 공개 재검증 완료까지 경과 시간은 437분 23초입니다. 후자는 첫 공개 검증의 GitHub 실행 목록 조회 시간 초과와 후속 확인까지의 대기를 포함하며 워크플로 실행 시간이 아닙니다. 첫 시도의 다운로드 파일은 `public-first-attempt/`, 중단 단계는 `public-first-attempt.json`에 남겼고 같은 파일로 공개 검증을 다시 완료했습니다.

공개 APK·스크린샷 ZIP·체크섬의 크기·SHA-256·GitHub 자산 digest·로컬 원본 바이트가 모두 같았습니다. APK 앱 ID·버전 1.0.11·코드 12·기존 인증서, ZIP 81개 PNG의 원본 바이트와 CRC, 체크섬 두 항목을 확인했습니다. 최신 공개 릴리즈는 v1.0.11이며 Android 검증 Actions는 수동 진단 전용을 유지하고 이 게시의 소스·태그 커밋에 자동 Android 검사가 생성되지 않았습니다. 근거: `commit-relationship.json`, `public-status.json`, `public-verification.json`, `public-verification-run.log`·JSON, `public-apk-signature.txt`, `public-apk-manifest.txt`, `remote-test-workflow.yml`.

수동 테스트용 `perilog_api36 / emulator-5554` 창을 열고 `:app:assembleDebug :app:assembleDebugAndroidTest -PcaptureScreenshots`로 빌드한 두 APK를 기존 앱에 덮어 설치했습니다. `PreviewSamples#load` 1개가 통과했으며 기존 입력·수정값·초안을 보존했습니다. 샘플 기간은 2026-09-23~2026-10-07이고 품목 7개·사용 구성 3개·기록 22건, 추가한 기록 2건·초안 0건입니다. 앱을 다시 실행해 `Status: ok`를 확인했으며 사용자가 조작할 수 있게 창을 남겼습니다. 다른 검사 기기는 종료했습니다. 근거: `manual-build.log`, `manual-samples.log`·JSON.

첫 샘플 준비는 앱 프로세스가 초기 시작을 마치지 못해 Android가 종료했습니다. `manual-events.txt`의 `failed to complete startup`과 `manual-samples-startup-anr.log`를 보존했고, 명령 종료 코드가 0이어도 `Process crashed`였으므로 통과로 처리하지 않았습니다. 이후 실제 앱 화면 준비를 UI 계층으로 확인하고 같은 앱·데이터로 샘플 준비를 다시 실행해 `OK (1 test)`와 보존 결과를 확인했습니다. 사용자 데이터를 초기화하지 않았습니다.

깨끗한 작업 트리의 빌드 커밋은 `1359113d848f2cebfdcb73907ce415d36cc6718b`입니다. 실행 ID `20261006T085059Z-v1.0.11-c185bda`, 담당 작업 폴더 `/Users/poyal/Workspace/personal/perilog`, 담당자는 이번 Codex 세션입니다. 로컬 검증은 `2026-10-06T08:50:59.831807+00:00`부터 `2026-10-06T13:01:28.074488+00:00`까지 250분 28초이며 실패 원인 조사·검사 보완·재실행을 포함합니다. 가상 자료와 검사 전용 에뮬레이터만 사용했습니다.

| 검사 | 최종 후보 결과 |
| --- | --- |
| 전체 단위·저장소 | 150개, 실패·오류·생략 0. Room API 31·35를 포함해 소스의 예상 이름과 실제 실행 목록 대조 |
| Lint·빌드 | Debug·Release 오류 0, 경고 각각 40·41개. 개발·검사용·기존 키로 서명한 Release APK 빌드 성공 |
| API 35 전체 UI | 기본·글씨 1.5배 각각 76개, 실패·오류·생략·예상 이름 누락 0 |
| API 36 지정 회귀 | 기본·글씨 1.5배 각각 16개. 상단 알림 4·설정 8·사용성 3·입력 초안 회전/Activity 재생성 1개, 실패·오류·생략·예상 이름 누락 0 |
| 실제 프로세스 복구 | `PASS: process 3408 -> 3683; unsaved product form restored, then discarded.` |
| 최종 서명 APK 신규 설치 | API 35의 빈 앱에 설치, 62.3kg 기록 저장·프로세스 재실행·재고·설정 진입, DB 무결성 확인 |
| 공개 v1.0.10 → 최종 APK | 삭제 없이 업데이트. 스키마 8·15개 테이블·55행·584개 기존 필드 값 모두 동일 |
| 백업·권한 | UID·최초 설치 시각·기존 알림·SAF 권한·DataStore 기존 키·보호 백업 보존. 같은 폴더에 새 백업 생성·기존 파일 바이트 유지 |
| 안내·문서·ZIP | 변경 화면 11장 촬영, 앱 안내 PNG 48장·9,194,877바이트 원본 일치. 배포 ZIP 81개 PNG 원본 일치·CRC 확인 |

최초 후보는 `clean :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease`를 실행했습니다. 검사 코드 보완 후 최종 커밋에서는 같은 작업을 clean 없이 다시 수행했습니다. 앱·단위 검사 소스는 같아 최종 단위 작업은 Gradle `UP-TO-DATE`였으며 이번 릴리즈에서 실행한 150개 XML과 소스 해시를 보존했습니다. 최초·최종 APK의 ZIP 항목 차이는 `META-INF/version-control-info.textproto`뿐이고 앱 코드·리소스는 같습니다. 최종 UI는 보완한 검사 코드로 다시 실행했고 신규 설치·업데이트는 아래 최종 APK 해시로 수행했습니다. 과거 릴리즈의 통과 기록을 이번 결과로 대신하지 않았습니다.

프로세스 복구 첫 실행은 입력 확인 단계의 UIAutomator 조회가 30초를 넘겨 중단됐고 실패 화면 수집도 끝나지 않았습니다. 별도 스크린샷 조회 역시 멈췄으며 에뮬레이터 로그에 `Failed to find ColorBuffer`가 남았습니다. 검사 전용 에뮬레이터만 데이터 초기화 없이 재시작해 화면 조회 정상화를 확인한 뒤 같은 소스와 APK로 해당 검사를 재실행했습니다. 첫 실패는 `process-restore-failed1.log`·JSON과 `process-failed1/`, `emulator35.log`에 보존했으며 통과 결과로 처리하지 않았습니다.

API 36 첫 실행은 기본 16개 통과 후 큰 글씨에서 진행이 멈췄고 별도 ADB logcat 조회도 15초 내 응답하지 않았습니다. 검사 기기를 종료해 중단한 큰 글씨 보고서는 5개·실패 1개이며 완료되지 않은 실행입니다. 실패는 연결 종료 시 진행 중이던 검사에 기록됐습니다. 이 자료는 `api36-interrupted1/`에 보존했습니다. 에뮬레이터 GPU 모드를 기존 `swiftshader_indirect`에서 지원되는 `swiftshader`로 바꾸고 데이터 초기화 없이 재시작한 뒤 부팅·화면 조회를 확인해 기본·큰 글씨 전체를 다시 검사했습니다. 중단 원인을 앱 결함이나 GPU 모드 하나로 확정하지 않았으며, 최종 통과는 재실행의 완전한 보고서만 사용했습니다.

신규 설치 전용으로 만든 `perilog_release1011`은 첫 부팅이 8분 넘게 완료되지 않아 APK 설치 전 종료했습니다. 부팅 로그에는 시스템 서비스 준비 대기가 남았고 크래시 로그는 비어 있었습니다. `fresh-install-boot-aborted.log`·JSON을 보존했으며 신규 설치 통과로 처리하지 않았습니다. 기존 검사 전용 `perilog_upgrade_audit`의 가상 자료를 별도 보관하고 출시 앱만 제거한 뒤 빈 앱 설치부터 다시 확인했습니다.

전환한 기기의 첫 APK 설치 후에는 Google Digital Wellbeing의 ANR 창이 앱을 가리고 UIAutomator 조회가 실패했습니다. `fresh-start-screen.png`·`fresh-start-window.txt`로 해당 패키지를 확인했고 앱 크래시 로그는 비어 있었습니다. 검사 기기의 Digital Wellbeing만 강제 종료하고 글씨 배율을 1.0으로 지정한 뒤 출시 앱을 제거해 신규 설치를 처음부터 다시 확인했습니다. 실패 자료는 `fresh-install-system-dialog.log`·JSON에 남겼습니다.

이어진 새 설치의 첫 실행 대기가 30초를 넘어 중단됐으며 현재 창은 `Application Not Responding: com.android.systemui`였습니다. `fresh-launch-window.txt`·화면과 `fresh-install-systemui-anr.log`·JSON을 보존했습니다. 설치 검증 API 35 기기는 앞서 API 35 전체 UI·프로세스 검사를 수행한 `swiftshader_indirect` 모드로 재시작했고, 로컬 설치 보조 도구의 첫 Activity 실행 대기를 120초로 늘렸습니다. 앱·APK·필수 검증 항목은 변경하지 않았습니다.

Lint 경고는 의존성·AGP 업데이트, 앱 언어 설정, Compose Modifier 인자, 호환 Drawable, API 조건, 위젯 중첩 weight·작은 글씨, KTX 관련 항목입니다. Release에는 단색 런처 아이콘 경고 1개가 추가됩니다. 전체 항목은 `final-lint-results-debug.xml`, `final-lint-results-release.xml`에 남겼습니다.

업데이트 자료는 공개 v1.0.10 APK의 실제 파일 선택기로 가상 백업을 가져와 준비했습니다. 음수·0·미입력, mL·g·kg, 서로 다른 과거 계산 기준, 취소 이력, 삭제한 기록의 사용 내역, 비활성 품목, 수정·미저장 초안, 비기본 설정을 포함합니다. 품목 6·구성 3·기록 13·사용 14·입고 3·실사 2·감사 4·초안 2·설정 1·진료과 2·치료 구성 1·예약 1·연락처 1·재고 조정 1·입고 요청 1행의 모든 ID·기존 필드를 비교했습니다. 재고 화면의 38·100·107·91·20EA가 그대로 유지됐습니다. 자동 백업의 전체 JSON은 `exportedAt`만 제외하고 동일하며, 수동 내보내기 파일을 새로 만들었다고 처리하지 않았습니다. DataStore는 업데이트 조회 캐시 `appUpdate`만 변경 허용했고 `notificationRequested`를 포함한 나머지 기존 키는 유지됐습니다. 내부 보호 백업 바이트, 알림 허용, 실제 파일 선택기로 얻은 SAF `persisted=0x3`도 유지됐습니다.

| 배포 파일 | 바이트 | SHA-256 |
| --- | ---: | --- |
| `perilog-1.0.11.apk` | 18,647,945 | `aaab999bebeb98f1511acbb915bd3a09e312d2d3fbcb353ca04247df070d59ee` |
| `perilog-1.0.11-screenshots.zip` | 13,958,424 | `5ebf374af6aec4f62234ec17c2be3deb979541487dd8261ee1cc21cde85d9a0d` |
| `perilog-1.0.11.sha256` | 182 | `909491528973b877f5d7a30077d4c70f3258ed46140c994cac974544cbbaf3f3` |

APK 서명은 직전 공개 v1.0.10과 같은 인증서 SHA-256 `79adbeead4f7fbc2a5bf99219ee2fdd909f14eb18817c2b4f6139edbce0937dd`입니다. [직전 공개 APK](https://github.com/poyal/perilog/releases/download/v1.0.10/perilog-1.0.10.apk)의 SHA-256은 `fa995dbb135193f9e3ee800c6f4decac61d4b90d6f4c11490f4557b1d5c53945`이며 공개 체크섬과 일치했습니다. 고정한 `dist/` 세 파일만 게시 대상으로 사용하며 서명 키·비밀번호·키 백업은 제외했습니다.

환경: macOS 27.0.1 arm64, Temurin JDK 17.0.14+7, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, SDK/Build Tools 36.0.0. arm64 에뮬레이터 API 35 `perilog_top_feedback / emulator-5580`은 기본·1.5배 전체 UI와 프로세스 복구, API 36 `perilog_update_api36 / emulator-5558`은 지정 회귀, API 35 `perilog_upgrade_audit / emulator-5582`는 서명 APK 신규 설치·업데이트에 사용했습니다. Gradle·ADB 검증 단계는 순차 실행했습니다.

근거는 `.tools/release-checks/20261006T085059Z-v1.0.11-c185bda/`의 `run.json`, `verification.md`, `candidate-build.log`, `candidate-final-build.log`, `unit-results/`, `unit-name-audit.json`, `api35-final/`, `api36/`, 각 `*-name-audit.json`, `process/`, `fresh/verification.json`, `upgrade/comparison.json`, `upgrade/backup-preservation.json`, `results-summary.json`, `report-hashes.json`, `artifact-manifest.json`, `candidate-apk-diff.json`입니다. 명령·종료 코드·시각은 단계별 JSON과 로그에 보존했습니다.

원격 Android 전체 진단은 미실행·불필요이며 수동 전용을 유지합니다. 최소 SDK·OS 호환 처리·업데이트 다운로드·게시 코드를 바꾸지 않아 API 31 실제 신규 설치, 별도 업데이트 다운로드 통합 검사와 게시 스크립트 검사는 추가하지 않았습니다. API 31 Robolectric 결과를 실기기 설치 결과로 처리하지 않았습니다. 통합 검증 명령·기록 자동 차단·작업 폴더 잠금은 아직 미구현이며, 실행별 로컬 보조 스크립트와 보고서 대조로 수동 확인했습니다. Galaxy Z 플립7 실물의 접기·펼치기·삼성 키보드·생체 인증·삼성 파일 선택기·장시간 절전은 이번 검사 범위에 포함되지 않습니다.

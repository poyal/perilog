# v1.0.12 최종 검증 결과

[사용자 매뉴얼](README.md) · [GitHub Release](https://github.com/poyal/perilog/releases/tag/v1.0.12) · [공통 개발 규칙](../../development.md) · [배포 절차](../../release.md)

- 공개 태그 커밋: `3d8514bc49994afef08dd8a56506d5eb36bf33cc`
- 아래 결과는 기존 최종 검증 기록을 정리한 것이며 이번 문서 정리에서 재실행한 결과가 아닙니다. 작업 로그·중간 후보·로컬 설치 파일은 보관하지 않습니다.

## 2026-10-07 v1.0.12 릴리즈 검증·게시 완료

버전 `1.0.12` · 코드 `13` · 앱 ID `com.poyal.perilog` · 스키마 `8`. 앱 잠금과 화면 보호를 독립 설정으로 분리하고, 최신 릴리즈와 다른 APK의 다운로드·검증·설치 상태가 표시되던 문제를 수정했습니다. 기존 잠금 설정에서 화면 보호 기본값을 보존합니다. README·사용 안내·앱 안내·버전 표시 화면을 갱신했습니다. 공개 파일도 같은 바이트로 검증했습니다.

[v1.0.12 공개 릴리즈](https://github.com/poyal/perilog/releases/tag/v1.0.12)의 게시와 공개 파일 재검증을 완료했습니다. 빌드 커밋은 `661492bdbda84309e9bc59a363c38a5ce1f69893`, 최종 소스 커밋은 `5cdcaf13ef017498d697eb67ed60e3ef1a1012ac`, 배포·태그 커밋은 `3d8514bc49994afef08dd8a56506d5eb36bf33cc`입니다. 빌드 후 차이는 `docs/releases/v1.0.12.md`와 `docs/releases/v1.0.16/AGENTS.md`뿐입니다. 배포 커밋은 최종 소스의 단일 자식이며 `.release/`의 이 버전 세 파일 추가만 포함합니다. 원격 annotated 태그도 같은 배포 커밋을 가리킵니다.

[게시 Actions](https://github.com/poyal/perilog/actions/runs/37560232962)는 APK를 다시 빌드하지 않고 검증한 파일을 게시했으며 성공했습니다. 공개 시각은 `2026-10-07T02:05:03Z`, 재다운로드 검증 완료는 `2026-10-07T02:06:57.452846+00:00`입니다. 게시 시작부터 공개까지 30초, 공개 파일 확인 완료까지 144초 걸렸습니다. 게시 직전 비인증 GitHub API 요청 한도 때문에 공개 웹·원격 Git으로 중복 버전과 실행 상태를 확인했고, 한도 초기화 뒤 REST API를 포함한 공개 검증을 완료했습니다.

공개된 APK·스크린샷 ZIP·체크섬 세 파일의 크기·SHA-256·GitHub 자산 digest·로컬 원본 바이트가 모두 일치합니다. APK 앱 ID `com.poyal.perilog`·버전 `1.0.12`·코드 `13`·기존 인증서, ZIP 81개 PNG의 원본 바이트·CRC와 체크섬 두 항목을 확인했습니다. 최신 공개 릴리즈는 v1.0.12이며, Android 검증 Actions는 수동 전용을 유지하고 이번 소스·태그 푸시로 자동 Android 검사가 생성되지 않았습니다. 근거는 `commit-relationship.json`, `preflight-remote.json`, `preflight-actions.html`, `publish-push.log`·JSON, `public-status.json`, `public-verification.json`, `public-verification-run.log`·JSON, `public-apk-signature.txt`, `public-apk-manifest.txt`, `remote-test-workflow.yml`입니다.

깨끗한 작업 트리에서 확정한 빌드 커밋은 `661492bdbda84309e9bc59a363c38a5ce1f69893`입니다. 실행 ID `20261007T010607Z-v1.0.12-43ec6d2`, 작업 폴더 `/Users/poyal/Workspace/personal/perilog`, 담당자는 이번 Codex 세션입니다. 로컬 준비·검증은 `2026-10-07T01:06:07.419309+00:00`부터 `2026-10-07T02:01:06.313617+00:00`까지 54분 58초입니다. 가상 자료와 검사 전용 에뮬레이터만 사용했고 과거 릴리즈의 통과 기록을 대신 사용하지 않았습니다.

| 검사 | 이번 후보 결과 |
| --- | --- |
| 전체 단위·저장소 | 159개, 실패·오류·생략 0. Room API 31·35와 이전 잠금 설정·백업 호환 포함. 소스의 예상 이름과 XML 실행 목록 일치 |
| Lint·빌드 | Debug·Release 오류 0, 경고 각각 40·41개. clean 후 개발·검사·기존 키 서명 Release APK 빌드 성공 |
| API 35 전체 UI | 기본·글씨 1.5배 각각 81개. 실패·오류·생략·이름 누락 0 |
| API 36 지정 회귀 | 기본·글씨 1.5배 각각 21개. 입력 초안·회전/재생성·설정·상단 알림·사용성·잠금/보호·업데이트 화면 포함 |
| API 31 지정 회귀 | 기본 글씨 5개. 잠금/보호·업데이트 UI 통과. Android 12의 보호 캡처 거부도 실제 확인 |
| 잠금·보호 동작 | 4가지 조합의 실제 스크린샷·화면 녹화, 보호 중 대화상자, 합성 기기 PIN 인증·취소·재진입·설정 독립 저장 확인. 최근 앱 화면 캡처도 보관 |
| 실제 다운로드 통합 | 실제 DownloadManager·로컬 HTTP 응답·APK 검증·개인 설치 사본·Android 설치 화면 연결 1개 통과. 이전 파일 보존·최신 다운로드 새 시작·공용 파일 변조 거부 확인 |
| 실제 프로세스 복구 | `PASS: process 10379 -> 10643; unsaved product form restored, then discarded.` |
| 최종 서명 APK 신규 설치 | API 35·31에서 62.3kg 기록 저장, 프로세스 재시작 후 유지, 재고·설정 진입과 DB 무결성 확인 |
| 기존 잠금 사용자 업데이트 | API 31에서 공개 1.0.11의 잠금 켜짐·새 필드 없음 → 서명 1.0.12 덮어 설치. 기기 PIN 인증 후 두 옵션 켜짐·보호 창 플래그 확인. 화면 보호만 끄고 저장해 잠금과 기존 설정 유지 |
| 공개 1.0.11 → 최종 APK 보존 | API 35, 15개 테이블·55행·584개 기존 필드 값·모든 ID 동일, UID·최초 설치 시각 유지 |
| 백업·권한 | 전체 JSON·재고·초안·DataStore 기존 키·내부 보호 백업·알림 권한·실제 SAF 폴더 권한 유지. 폴더 재연결 없이 새 백업 생성, 기존 파일 바이트 보존 |
| 문서·산출물 | 현재 참조하는 앱 안내 PNG 48장 원본 일치·로컬 문서 링크 확인. 스크린샷 ZIP 81장 CRC·원본 바이트 일치. 공개와 동일 인증서·앱 ID·버전·해시 확인 |

Android 16 첫 실행은 시스템 UI 시작 실패로 기본 21개 중 7개가 실패했습니다. `api36-systemui-events.txt`에 검사 시작 직전 `com.android.systemui ... failed to complete startup` ANR이 기록됐고, `api36-failure-window.txt`·화면에서 시스템 응답 없음 창이 앱을 가로막는 것을 확인했습니다. 처음 실패 자료는 `api36-systemui-anr/`과 동명 로그·JSON에 보존했습니다. 검증 전용 AVD를 데이터 초기화 없이 재시작하고 Android 설정 화면 진입·뒤로 이동 및 시스템 창 정상 상태를 확인한 뒤, 같은 코드·APK로 기본·큰 글씨 21개 전체 범위를 다시 검사했습니다. 처음 실패를 통과 결과에 합산하지 않았습니다.

API 31 서명 APK 첫 새 설치 확인은 UIAutomator가 하단 탭의 글자 bounds를 `[0,0][0,0]`으로 반환해 조작 도구가 탭을 찾지 못했습니다. 실제 스크린샷에는 탭이 보였고 상위 클릭 가능한 탭 영역에는 유효한 bounds가 있었습니다. 로컬 조작 도구를 해당 상위 영역을 누르도록 보완하고 새 설치부터 다시 검사했습니다. 최초 자료는 `fresh31-label-bounds/`와 `fresh-install31-label-bounds.*`에 보존했습니다. 앱 코드·APK는 변경하지 않았습니다.

API 31 기존 잠금 이전 검사는 두 옵션 켜짐·보호 플래그 확인 후 UID 비교 보조 도구가 `appId`를 찾지 못해 중단됐습니다. Android 12의 패키지 정보에는 해당 값이 `userId`로 표시되어 두 이름을 모두 지원하도록 로컬 파서만 수정하고 구버전 설치부터 다시 확인했습니다. 최초 자료는 `legacy-lock-package-format/`와 동명 로그·JSON에 보존했습니다.

기존 잠금 검사의 재시작 준비 중 출시 패키지가 없는 상태에서 Android 12 `pm path`가 종료 코드 1을 반환해 로컬 도구가 중단됐습니다. 설치 목록의 정확한 패키지 이름으로 존재 여부를 확인하도록 보완했습니다. `legacy-lock-empty-package.*`에 보존했으며 이 중단도 통과로 세지 않았습니다.

이어진 기존 잠금 검사 준비에서 앞선 시도의 잠금 설정·인증 창이 남아 첫 화면 진입이 중단됐습니다. `legacy-lock-stale-setup/`·동명 로그와 `legacy-current-state/`에 보존했고, 전용 AVD의 출시 패키지를 명시적으로 강제 종료·제거한 결과를 `legacy-reset-diagnostic.json`에 기록했습니다. 구버전 설치 후 가상 자료 준비 단계에서만 앱 데이터를 비워 초기 상태를 확정한 뒤 잠금을 켰습니다. 이후 후보 APK 덮어 설치·비교 중에는 삭제나 데이터 초기화를 하지 않았습니다.

실행 명령은 `scripts/build.sh clean :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease`, `scripts/test-e2e.sh all`(API 35 전체, API 36 지정 21개), `scripts/test-e2e.sh normal`(API 31 지정 5개), `scripts/test-process-restore.py`, `scripts/test-updates.sh`입니다. 시각·전체 명령·종료 코드는 각 단계 JSON/로그, 실행 이름과 실패·생략 수는 XML 및 이름 대조 기록에 보관했습니다. 스크린샷·녹화 증거도 실행 폴더에 보관했습니다.

업데이트 자료는 이전 공개 앱의 실제 파일 선택기로 가져온 가상 백업입니다. 기계·추가투석, 음수·0·미입력, mL·g·kg, 과거 계산 기준, 취소·삭제 기록의 사용, 비활성 품목, 수정·미저장 초안, 기본값이 아닌 설정을 포함합니다. DB의 모든 기존 값은 변하지 않았습니다. 전체 백업 JSON은 `exportedAt`과 계획한 `preferences.screenProtection` 추가만 차이가 있습니다. 새 필드는 기존 `lock` 값과 같음을 확인한 뒤 나머지 전체 값을 비교했습니다. DataStore는 업데이트 조회 캐시 `appUpdate`만 변화 허용하고 `notificationRequested`를 포함한 기존 키를 비교했습니다. 내부 보호 백업·외부 기존 백업 파일은 바이트 단위로 동일하며 SAF `persisted=0x3`와 알림 허용도 유지됐습니다.

Lint 경고는 의존성·AGP 업데이트 안내, 앱 언어 설정, Compose Modifier 인자, 호환 Drawable, API 조건, 위젯 중첩 weight·작은 글씨, KTX와 Release 단색 런처 아이콘 항목입니다. 전체 항목은 `lint-results-debug.xml`·`lint-results-release.xml`에 남겼습니다.

| 배포 파일 | 바이트 | SHA-256 |
| --- | ---: | --- |
| `perilog-1.0.12.apk` | 18,821,537 | `83f1cb18582529bf27978d5e9a17d1431cb52f4cd81d3b78725260ed32621cfe` |
| `perilog-1.0.12-screenshots.zip` | 14,138,194 | `036358acbdf0f607642ad94925aeedc74ecc1e272daaacaae6474498858481cf` |
| `perilog-1.0.12.sha256` | 182 | `7173295652090b48c6c6b3c1e2ab612fa1f45f34b58cd345990c9c022acfd5af` |

APK는 로컬에서 고정한 `dist/perilog-1.0.12.apk`이며 신규 설치·업데이트 검사는 이 파일로 수행했습니다. 인증서 SHA-256은 직전 공개 APK와 같은 `79adbeead4f7fbc2a5bf99219ee2fdd909f14eb18817c2b4f6139edbce0937dd`입니다. 직전 [공개 1.0.11 APK](https://github.com/poyal/perilog/releases/download/v1.0.11/perilog-1.0.11.apk)의 SHA-256은 `aaab999bebeb98f1511acbb915bd3a09e312d2d3fbcb353ca04247df070d59ee`이며 다시 받은 공개 체크섬과 일치했습니다. 키·비밀번호·키 백업은 Git과 세 게시 파일에 포함하지 않았습니다.

환경: macOS 27.0.1 arm64, JDK 17.0.14, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, SDK/Build Tools 36.0.0. arm64 AVD API 35 `perilog_top_feedback / emulator-5580`, API 36 `perilog_update_api36 / emulator-5558`, API 31 `perilog_release_api31 / emulator-5564`, 설치·보존용 API 35 `perilog_upgrade_audit / emulator-5582`를 사용했습니다. Gradle·ADB 검증 단계는 순차 실행했습니다.

근거: `.tools/release-checks/20261007T010607Z-v1.0.12-43ec6d2/`의 `run.json`, `verification.md`, `candidate-build.log`, `unit-results/`, `unit-name-audit.json`, `api35/`, `api36/`, `api31/`, `*-name-audit.json`, `android-updates-results/`, `process/`, `fresh/verification.json`, `fresh31/verification.json`, `legacy-lock/verification.json`, `upgrade/comparison.json`, `upgrade/backup-preservation.json`, `results-summary.json`, `report-hashes.json`, `artifact-manifest.json`입니다.

원격 Android 전체 진단은 미실행·불필요이며 수동 전용 정책을 유지합니다. 게시 코드를 변경하지 않아 게시 스크립트 단위 검사와 actionlint 추가 실행은 제외했습니다. 통합 검증 명령·검증 기록 자동 차단·작업 폴더 간 잠금은 미구현이며, 실행별 로컬 보조 도구와 보고서 대조로 수동 확인했습니다. Galaxy Z 플립7 실물의 접기·펼치기·삼성 키보드·생체 인증·삼성 파일 선택기·장시간 절전과 실물 기기의 녹화·공유 앱별 동작은 별도 확인 범위입니다. 에뮬레이터 PIN 인증을 실제 생체 인증 결과로 처리하지 않았습니다.

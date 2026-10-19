# v1.0.13 최종 검증 결과

[사용자 매뉴얼](README.md) · [GitHub Release](https://github.com/poyal/perilog/releases/tag/v1.0.13) · [공통 개발 규칙](../../development.md) · [배포 절차](../../release.md)

- 공개 태그 커밋: `17be0b7c1f7fbf714dab6bb45d7a827bdbe42f6a`
- 아래 결과는 기존 최종 검증 기록을 정리한 것이며 이번 문서 정리에서 재실행한 결과가 아닙니다. 작업 로그·중간 후보·로컬 설치 파일은 보관하지 않습니다.

## 2026-10-07 v1.0.13 릴리즈 검증·게시 완료

버전 `1.0.13` · 코드 `14` · 앱 ID `com.poyal.perilog` · DB 스키마 `9`. 휴대폰 캘린더 연동, 버튼 규격·간격 통일, 일정 카드의 더보기 메뉴, 활력 상태 수정·저장 아이콘을 포함합니다. 캘린더는 페리로그에서 변경한 예약을 보내는 단방향 반영입니다. Google 직접 연결의 실제 OAuth 인증·웹 반영과 양방향 동기화를 검증 완료 기능으로 표시하지 않습니다. 공개된 세 파일도 로컬 검증본과 동일함을 확인했습니다.

[v1.0.13 공개 릴리즈](https://github.com/poyal/perilog/releases/tag/v1.0.13) 게시와 공개 파일 재검증을 완료했습니다. 빌드 커밋은 `56afbe22c001f88f5d16ee13d719d09ca915c3e1`, 최종 소스 커밋은 `95d4f9cf3ecbda6319627fbbce91fd4e53d638b8`, 배포·태그 커밋은 `17be0b7c1f7fbf714dab6bb45d7a827bdbe42f6a`입니다. 빌드 이후 차이는 `docs/releases/v1.0.13.md`와 `docs/releases/v1.0.16/AGENTS.md` 두 문서뿐입니다. 배포 커밋은 최종 소스의 단일 자식이며 `.release/`의 해당 APK·화면 ZIP·체크섬 세 파일 추가만 포함합니다. 원격 annotated 태그도 같은 배포 커밋을 가리킵니다.

[게시 Actions](https://github.com/poyal/perilog/actions/runs/37607050058)는 재빌드 없이 검증한 파일을 게시했고 성공했습니다. 공개 시각은 `2026-10-07T10:23:33Z`, 재다운로드 검증 완료는 `2026-10-07T10:24:26.781897+00:00`입니다. 태그 푸시 시작부터 공개까지 30초, 공개 파일 확인 완료까지 84초 걸렸습니다. 중복 실행을 피하려고 main과 태그만 한 번 푸시했으며 release 브랜치는 원격으로 푸시하지 않았습니다.

공개 APK·화면 ZIP·체크섬 세 파일의 크기·SHA-256·GitHub 자산 digest·로컬 원본 바이트가 모두 일치했습니다. APK 앱 ID `com.poyal.perilog`·버전 `1.0.13`·코드 `14`·기존 인증서, ZIP 88개 PNG의 원본 바이트·CRC 및 체크섬 두 항목을 확인했습니다. 최신 공개 릴리즈는 v1.0.13입니다. Android 검증 Actions는 수동 전용을 유지하며 이번 소스·태그 푸시로 자동 Android 검사가 생성되지 않았습니다. 게시 직전 공개 API·원격 Git·Actions로 버전 중복과 동시 게시를 확인했고, 공개 API로 보이지 않는 초안·기존 자산은 인증된 게시 스크립트가 비교한 뒤 공개했습니다.

게시 근거는 `commit-relationship.json`, `prepublish.json`, `publish-push.log`·JSON, `publish-actions-status.json`, `public-status.json`, `public-verification.json`, `public-verification-run.log`·JSON, `public-apk-signature.txt`, `public-apk-manifest.txt`, `remote-test-workflow.yml`입니다. 게시 후 이 결과만 main의 문서에 추가했고 릴리즈 태그와 산출물은 변경하지 않았습니다.

실행 ID `20261007T092436Z-v1.0.13-906c19c`, 작업 폴더 `/Users/poyal/Workspace/personal/perilog`, 담당자는 이번 Codex 세션입니다. 깨끗한 작업 트리에서 고정한 빌드 커밋은 `56afbe22c001f88f5d16ee13d719d09ca915c3e1`입니다. 로컬 준비·검증은 `2026-10-07T09:24:36.467817+00:00`부터 `2026-10-07T10:22:24.838408+00:00`까지 57분 48초입니다. 과거 자료 중 가상 입력 데이터와 로컬 보조 도구 구조만 재사용했고, 모든 통과 결과는 이번 후보로 새로 실행했습니다.

| 검사 | 이번 후보 결과 |
| --- | --- |
| 전체 단위·저장소 | 195개, 실패·오류·생략 0. Room API 31·35, DB 이전 경로·캘린더 작업 큐·충돌 및 소유권 포함. 소스의 예상 이름과 실제 XML 일치 |
| Lint·빌드 | Debug·Release 오류 0, 경고 각각 40·41개. clean 후 개발·검사·기존 키 서명 APK 빌드 성공 |
| API 35 전체 UI | 기본·1.5배 글씨 각각 84개, 실패·오류·생략·이름 누락 0. 전체 AppFlow·Appointment·Calendar 및 기본 테스트 대상 포함 |
| API 36 지정 회귀 | 기본·1.5배 글씨 각각 29개, 실패·오류·생략·이름 누락 0. 키보드·입력 초안·회전·설정·상단 안내·사용성·잠금/보호·업데이트·캘린더·일정 메뉴 포함 |
| API 31 지정 회귀 | 기본 글씨 8개, 실패·오류·생략·이름 누락 0. Calendar·Privacy·Updates 전체 |
| 잠금·보호 | 4가지 조합의 스크린샷·녹화, 보호 중 대화상자, 합성 PIN 인증·취소·재진입. API 31의 보호 캡처 거부도 검사 |
| 실제 다운로드 통합 | 1개 통과. 실제 DownloadManager·로컬 HTTP·APK 검사·설치 화면 연결, 기존 공용 파일 보존·변조 거부 |
| 실제 프로세스 복구 | `PASS: process 10335 -> 10617; unsaved product form restored, then discarded.` |
| 최종 서명 APK 신규 설치 | API 31·35에서 62.3kg 기록 저장·프로세스 재시작 후 유지·재고 및 설정 진입·DB 무결성 확인 |
| 공개 1.0.12 → 최종 APK | API 35에서 삭제 없이 덮어 설치. 스키마 8→9, 기존 15개 테이블·55행·584개 필드 값과 모든 ID 동일. 새 캘린더 테이블 4개는 빈 상태로 생성 |
| 백업·권한 | UID·최초 설치 시각·전체 JSON·재고·초안·DataStore 기존 키·보호 백업·알림·실제 SAF 권한 유지. 기존 파일 바이트 보존과 폴더 재선택 없는 새 백업 생성 확인 |
| 안내·문서·산출물 | 앱 안내 참조 PNG 50장 원본 일치·로컬 문서 링크 확인. 현재 매뉴얼에서 쓰는 PNG 88장 ZIP CRC·원본 일치. APK 앱 ID·버전·기존 인증서·해시 확인 |

실제 명령은 `scripts/build.sh clean :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease`, `scripts/test-e2e.sh all`(API 35 전체·API 36 지정 범위), `scripts/test-e2e.sh normal`(API 31 지정 범위), `scripts/test-process-restore.py`, `scripts/test-updates.sh`입니다. 모든 UI 실행은 `PERILOG_SERIAL`과 실행별 결과 경로를 지정했고, API 36·31은 `PERILOG_TEST_TARGET`도 지정했습니다. 단계별 JSON에 전체 명령·시작/종료 시각·종료 코드가 있으며 XML 이름 대조로 누락·생략을 별도 확인했습니다. 지난 릴리즈보다 단위 검사는 159→195개, API 35 전체 UI는 81→84개로 늘었습니다.

업데이트 가상 자료는 이전 공개 앱의 실제 파일 선택기로 가져왔습니다. 기계·추가투석, 음수·0·미입력, mL·g·kg, 과거 계산 기준, 취소·삭제 기록의 사용 내역, 비활성 품목, 미저장·수정 초안, 비기본 설정을 모두 포함합니다. 기존 DB 15개 테이블의 필드별 값·ID·행 수는 완전히 같았고, 전체 백업 JSON은 `exportedAt`만 제외해 동일했습니다. DataStore는 업데이트 조회 캐시 `appUpdate`만 변화 허용하고 `notificationRequested`를 포함한 기존 키를 비교했습니다. 내부 보호 백업과 외부 기존 백업 파일은 바이트가 같았으며 SAF `persisted=0x3`·알림 허용도 유지됐습니다. 새 캘린더 네 테이블은 백업 자료에 추가하지 않으며 기존 기록을 자동 재전송하지 않습니다.

최초 APK 메타데이터 확인은 로컬 보조 도구가 `aapt2`의 최소 API 항목 이름을 `sdkVersion`으로 가정해 중단됐습니다. 실제 출력은 `minSdkVersion:31`이며 앱 ID·버전·기존 인증서는 일치했습니다. 도구의 항목 이름만 수정하고 동일하게 고정한 APK의 해시를 다시 확인한 뒤 검증을 계속했습니다. 앱 소스나 APK는 변경하지 않았습니다. 근거는 `run-checks-first-attempt.py`, `aapt2.txt`, `frozen-apk.json`입니다.

추가 설치 파일 대조는 첫 검증 세션 종료 후 에뮬레이터 연결이 닫혀 실행하지 못했고, 기기를 다시 시작한 직후 패키지 경로 조회도 성공하지 않았습니다. 이를 통과로 세지 않았습니다. 별도 재현의 첫 시도는 후보 APK를 설치하기 전 공개 1.0.12의 첫 설정 화면을 찾지 못해 중단됐으며 UI 계층에는 Android 홈이 보였습니다. 해당 로그·화면을 보존하고 에뮬레이터 세션을 독립적으로 유지한 뒤, Android 설정 진입·뒤로 이동을 확인하고 실제 앱 창 준비를 기다리도록 로컬 조작 도구를 보완했습니다. 같은 공개 APK→고정 후보의 전체 보존 비교를 별도 폴더에서 다시 통과했고, 설치된 `base.apk`도 직접 읽어 원본 바이트·SHA-256 일치를 확인했습니다. 앱 소스·APK는 바꾸지 않았습니다. 근거는 `additional-audit-incidents.json`, `installed-proof-first-attempt.log`·JSON, `upgrade-proof-first-attempt/`, `installed-proof-health.txt`, `installed-proof.log`·JSON, `upgrade-proof/comparison.json`, `installed-apk-identity.json`입니다.

Lint 경고는 의존성·AGP 최신 버전 안내, 앱 언어 설정, Compose Modifier 인자, Drawable 호환, API 조건, 위젯 중첩 weight·작은 글씨, KTX 및 Release 단색 런처 아이콘입니다. 전체 항목은 두 Lint XML에 보관했습니다.

| 배포 파일 | 바이트 | SHA-256 |
| --- | ---: | --- |
| `perilog-1.0.13.apk` | 19,538,235 | `9980a6f305365af1af11c79e55457d41f4be424dba17589baa201d6a65c59873` |
| `perilog-1.0.13-screenshots.zip` | 15,168,019 | `c498d84aa55486729235fb896513228b2208f0dcd5d06dec2d2f713f2ffef65b` |
| `perilog-1.0.13.sha256` | 182 | `34e824009bf5a9df876b2d9d1d7b2bb7d872dba3cc9a901a31cbe32af381539c` |

신규 설치·업데이트 검사는 고정한 `dist/perilog-1.0.13.apk`를 사용했습니다. 기존 공개 APK와 같은 인증서 SHA-256은 `79adbeead4f7fbc2a5bf99219ee2fdd909f14eb18817c2b4f6139edbce0937dd`입니다. 직전 [공개 1.0.12 APK](https://github.com/poyal/perilog/releases/download/v1.0.12/perilog-1.0.12.apk)의 SHA-256은 `83f1cb18582529bf27978d5e9a17d1431cb52f4cd81d3b78725260ed32621cfe`이며 공개 체크섬·GitHub digest와 일치했습니다. 키·비밀번호·키 백업은 Git과 배포 파일에 포함하지 않았습니다.

환경: macOS 27.0.1 arm64, JDK 17.0.14, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, SDK/Build Tools 36.0.0. arm64 AVD API 35 `perilog_calendar_test / emulator-5580`, API 36 `perilog_update_api36 / emulator-5558`, API 31 `perilog_release_api31 / emulator-5564`, 서명 설치·보존 API 35 `perilog_upgrade_audit / emulator-5582`를 사용했습니다. 기본 글씨는 1.0, 큰 글씨는 1.5입니다. Gradle·ADB 검증 단계는 순차 실행했고, 사용자용 `perilog_api36 / emulator-5554`는 검증 대상으로 사용하지 않았습니다.

근거는 `.tools/release-checks/20261007T092436Z-v1.0.13-906c19c/`의 `run.json`, `verification.md`, `candidate-build.log`, `unit-results/`, `unit-name-audit.json`, `api35/`, `api36/`, `api31/`, `*-name-audit.json`, `android-updates-results/`, `process/`, `fresh/verification.json`, `fresh31/verification.json`, `upgrade/fixture-coverage.json`, `upgrade/comparison.json`, `upgrade/backup-preservation.json`, `results-summary.json`, `report-hashes.json`, `artifact-manifest.json`입니다.

원격 Android 진단은 미실행·불필요이며 수동 전용을 유지합니다. 게시 스크립트·워크플로는 바꾸지 않아 게시 코드 단위 검사와 actionlint 추가 실행은 제외했습니다. 통합 검증 명령·기록 자동 차단·작업 폴더 간 잠금은 미구현이며 이번 실행의 로컬 보조 도구와 보고서 대조로 수동 확인했습니다. Google Cloud 설정·실제 계정 OAuth, 구글 웹 반영, Galaxy Z 플립7 실물의 삼성 캘린더·키보드·접기/펼치기·생체 인증·장시간 절전은 별도 확인 범위입니다. 가상 CalendarProvider·HTTP 검사나 에뮬레이터 PIN 인증을 실제 구글 인증·실물 생체 인증의 통과로 간주하지 않습니다.

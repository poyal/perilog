# v1.0.15 최종 검증 결과

[사용자 매뉴얼](README.md) · [GitHub Release](https://github.com/poyal/perilog/releases/tag/v1.0.15) · [공통 개발 규칙](../../development.md) · [배포 절차](../../release.md)

- 공개 태그 커밋: `a75eba4ca9bad48abcd4e5ab0e673f1e7e7c470d`
- 아래 결과는 기존 최종 검증 기록을 정리한 것이며 이번 문서 정리에서 재실행한 결과가 아닙니다. 작업 로그·중간 후보·로컬 설치 파일은 보관하지 않습니다.

## 2026-10-17 v1.0.15 릴리즈 검증·게시 완료

[v1.0.15 공개 릴리즈](https://github.com/poyal/perilog/releases/tag/v1.0.15) 게시와 공개 파일 재검증을 완료했습니다. 빌드 커밋은 `cf2bf12963e81c3e1d082f4127b7d61996c330d8`, 최종 소스 커밋은 `51bcf27386d90755702f9dec39c25f11d4675494`, 배포·태그 커밋은 `a75eba4ca9bad48abcd4e5ab0e673f1e7e7c470d`입니다. 빌드 이후 차이는 `docs/releases/v1.0.15.md`와 `docs/releases/v1.0.16/AGENTS.md` 및 `docs/e2e.md` 세 문서뿐입니다. 배포 커밋의 단일 부모는 최종 소스이며 `.release/`의 해당 버전 세 파일 추가만 포함합니다. 원격 annotated 태그 역참조도 같은 배포 커밋입니다.

[게시 Actions](https://github.com/poyal/perilog/actions/runs/39010758801)가 성공했고 재빌드 없이 검증한 파일을 게시했습니다. 공개 시각은 `2026-10-17T06:40:30Z`, 공개 파일 확인 완료는 `2026-10-17T06:41:34.000049+00:00`입니다. 태그 푸시 시작부터 공개 파일 확인까지 96.4초, 게시 작업은 24초 걸렸습니다. 게시용 main과 태그를 한 번의 원자적 푸시로 반영했고, release 브랜치는 원격에 푸시하지 않았습니다.

공개 APK·화면 ZIP·체크섬 세 파일의 크기·SHA-256·GitHub digest·로컬 원본 바이트가 모두 일치했습니다. APK 앱 ID·버전 1.0.15·코드 16·최소 API 30·대상 API 36·기존 인증서, ZIP의 PNG 90장 전체 원본 바이트·CRC와 체크섬 두 항목을 확인했습니다. 최신 정식 릴리즈는 v1.0.15이며 초안·사전 공개가 아닙니다. 원격 Android 검증 워크플로의 수동 전용 설정과 이번 main·태그 푸시로 자동 검사가 생성되지 않은 것도 확인했습니다.

게시 직전 공개 API·원격 Git·Actions에서 동일 버전과 동시 게시를 확인했습니다. 공개 API로 보이지 않는 초안·기존 자산은 인증된 게시 스크립트가 해시와 재다운로드 바이트를 비교한 뒤 공개합니다. 근거는 `commit-relationship.json`, `publication-preflight.json`, `publish-push.log`·JSON, `publication-run.json`, `public-release.json`, `public-verification.json`, `public-apk-signature.txt`, `public-apk-manifest.txt`, `remote-tag-verification.json`, `remote-test-workflow.yml`입니다. 게시 확인 결과만 main의 후속 문서 커밋으로 남기며 공개 태그와 산출물은 유지합니다.

버전 **1.0.15 · 코드 16 · 최소 API 30 · 대상 API 36 · DB 스키마 9**입니다. 치료 기록의 명시적 저장·미저장 변경 확인·시계판 평균저류시간 선택, 기존 초안 보존과 앞선 Android 11 지원 개발 변경을 포함합니다. README의 지원 버전과 사용 절차, 앱 도움말과 변경 화면을 반영했습니다. 혈압 한쪽 입력 시 반대쪽 0 자동 입력은 수정 전 재현되지 않아 공통 숫자 입력을 변경하지 않고 독립 값 보존 검사를 추가했습니다.

실행 ID `20261017T060309Z-v1.0.15-cf2bf12`, 작업 폴더 `C:/Users/ttt45/Workspace/personal/perilog`, 담당자는 이번 Codex 세션입니다. 깨끗한 작업 트리의 빌드 커밋은 `cf2bf12963e81c3e1d082f4127b7d61996c330d8`입니다. 검증 시작 `2026-10-17T06:03:09.679831+00:00`, 종료 `2026-10-17T06:39:00.684562+00:00`, 소요 시간 35분 51초입니다. 사용자 데이터 없이 가상 자료와 검증 전용 AVD만 사용했습니다.

| 검사 | 실제 결과 |
| --- | --- |
| 전체 단위·저장소 | 250개, 실패·오류·생략 0. Room API 30·31·35 포함. 소스 예상 이름과 XML 대조 |
| 빌드·Lint | clean 후 Debug·AndroidTest·기존 키 서명 Release 빌드. Debug/Release 오류 0, 경고 50·51개 |
| API 35 전체 UI | 기본·1.5배 각각 100개, 실패·생략·누락 0. 544.1초·554.2초 |
| API 36 입력 회귀 | 기본·1.5배 각각 7개, 실패·생략·누락 0. 회전·키보드·미저장 팝업·도움말·시간 선택·위젯 이동 |
| 실제 프로세스 복구 | `PASS: process 8064 -> 8387; unsaved product form restored, then discarded.` |
| 서명 APK 신규 설치 | API 30·31·35에서 62.3kg 기록 저장·프로세스 재시작 후 유지, 재고·설정 진입, 스키마 9 확인 |
| 공개 1.0.14 → 후보 덮어 설치 | API 35, 삭제 없이 업데이트. 19개 테이블·37행·341개 필드·모든 ID 보존, DB 무결성 ok |
| 백업·권한 | UID·최초 설치 시각·재고·전체 JSON·기존 초안·DataStore·보호 백업·알림·SAF 권한 보존. 같은 폴더 백업 1→2개, 이전 파일 바이트 유지 |
| APK 일치·정렬 | 신규·업데이트 후 base.apk와 고정 APK 바이트 일치. ZIP·64비트 라이브러리 4개의 16KB 정렬 통과(실제 16KB OS 검사는 별도) |
| 문서·산출물 | 로컬 링크 413개, 안내 이미지 52장 원본 일치, 화면 ZIP 90장 CRC·원본 바이트 일치, 체크섬 두 항목 |

Windows Python 3.13.7 및 Gradle Wrapper로 실제 검사했습니다. 빌드 명령은 `gradlew.bat --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease -PcaptureScreenshots --console=plain`입니다. UI는 실행 자료에 보관한 `release-v115-ui-runner.py`로 현재 `scripts/test-e2e.sh`의 12개 클래스 전체 메서드를 실행하고 예상/실행 목록·상태를 XML/HTML/JSON으로 대조했습니다. API 36은 시작 전에 정한 7개 회귀 범위입니다. 실제 프로세스 종료는 `scripts/test-process-restore.py`, 서명 APK는 로컬 조작 도구로 검증했습니다. 미구현 통합 검증 명령을 실행했다고 기록하지 않습니다.

첫 빌드 실행은 Windows에서 상대 bat 경로를 실행하지 못해 중단됐습니다. 절대 경로로 수정한 실행을 채택했으며 첫 로그를 보존했습니다. aapt2의 최소 SDK 출력 필드명 확인을 보완했고 APK 바이트는 바꾸지 않았습니다. Android 11 신규 설치의 첫 준비 단계는 지원하지 않는 화면 회전 shell 명령으로 중단됐습니다. 공통 설정 명령으로 검사 도구만 보완하여 같은 APK로 새 설치부터 재검사했습니다. 첫 결과 `api30-fresh.log`·JSON과 `api30-signed/`, 보완 전 도구를 보존했고 `api30-fresh-retry.log`·JSON과 `api30-signed-retry/`를 채택했습니다. Lint 경고의 실제 항목별 목록은 `build-verification.json`과 두 Lint XML에 보존했습니다.

구버전 가상 자료는 실제 Android 파일 선택기로 가져왔습니다. 필수 9개 테이블 모두 데이터가 있으며 기계·추가투석, 음수·0·미입력, mL·g·kg, 과거 계산 기준, 취소·삭제 기록의 사용 이력, 비활성 품목, 미저장·수정 초안과 비기본 설정을 포함합니다. 전체 JSON은 exportedAt만 제외해 비교했고 DataStore는 appUpdate 조회 메타데이터 변화만 허용했습니다. 재고 37·0·-2·22와 실제 SAF persisted=0x3를 확인했습니다.

| 배포 파일 | 바이트 | SHA-256 |
| --- | ---: | --- |
| `perilog-1.0.15.apk` | 19,971,283 | `85f50cface3b7e50bf5a94ffdda1ce082a323ce763c321ca82e0f02fd6f7be4e` |
| `perilog-1.0.15-screenshots.zip` | 15,364,414 | `0fd3694d2fb0b86eb7162809b1217c7a940252091423ff48984a50d142956289` |
| `perilog-1.0.15.sha256` | 182 | `7ed000d5e3ed8c7a61f728922f0655b1b6f0fa6c099b258bf3ffe86bf4352628` |

APK는 `dist/perilog-1.0.15.apk`로 고정했습니다. 공개 1.0.14 APK SHA-256은 `fde62965c620162202fb242c5e49eaf9c120bba42fdc7237bf072607a2cb0f0d`이며 후보와 인증서 SHA-256 `79adbeead4f7fbc2a5bf99219ee2fdd909f14eb18817c2b4f6139edbce0937dd`가 같습니다. APK·스크린샷 ZIP·체크섬 세 파일만 배포하며 키·비밀번호·검사 DB는 포함하지 않습니다.

환경은 Windows AMD64, JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, SDK/Build Tools 36.0.0입니다. 실제 도구 출력은 `tools-and-previous.json`에 있습니다. x86_64 전용 AVD `Perilog_Changes`(API 35/5560), `Perilog_Release36`(API 36/5562), `Perilog_Widget31`(API 31/5564), `Perilog_Release30`(API 30/5566)을 사용했습니다. 이번 릴리즈에서 Android 12L·13·14·17 또는 arm64 전체 검사를 재수행한 것으로 표기하지 않습니다. 이전 호환성 검증은 별도 기록입니다.

근거는 `.tools/release-checks/20261017T060309Z-v1.0.15-cf2bf12/`의 `run.json`, `verification.md`, `candidate-build-run2.log`, `unit-results/`, `build-verification.json`, `lint-debug.xml`, `lint-release.xml`, API별 UI 폴더의 `summary.json`·`results.xml`·`report.html`, `process-evidence/`, `fresh-install.json`, `api30-signed-retry/`, `api31-signed/`, `upgrade-before/`, `upgrade-after/`, `upgrade-comparison.json`, `automatic-backup-preservation.json`, `installed-apk-identity.json`, `artifact-manifest.json`, `results-summary.json`, `report-hashes.json`입니다.

원격 Android 진단은 미실행·불필요이며 수동 전용을 유지합니다. 통합 검증 명령·검증 기록 자동 차단·작업 폴더 간 잠금은 미구현이므로 자료와 커밋 관계를 직접 대조합니다. Galaxy Z 플립7 실물 접기·펼치기, 삼성 키보드·생체 센서·장시간 절전, 실제 Google OAuth·Play 심사는 별도 미검증 범위입니다.

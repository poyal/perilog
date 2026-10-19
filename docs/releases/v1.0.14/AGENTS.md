# v1.0.14 최종 검증 결과

[사용자 매뉴얼](README.md) · [GitHub Release](https://github.com/poyal/perilog/releases/tag/v1.0.14) · [공통 개발 규칙](../../development.md) · [배포 절차](../../release.md)

- 공개 태그 커밋: `f88636c058a6282daa3565d6ceadedfae2bd3bba`
- 아래 결과는 기존 최종 검증 기록을 정리한 것이며 이번 문서 정리에서 재실행한 결과가 아닙니다. 작업 로그·중간 후보·로컬 설치 파일은 보관하지 않습니다.

## 2026-10-11 v1.0.14 릴리즈 검증·게시 완료

[v1.0.14 공개 릴리즈](https://github.com/poyal/perilog/releases/tag/v1.0.14) 게시와 공개 파일 재검증을 완료했습니다. 빌드 커밋은 `ae6a6038d81af3dd560a961ede28654a8c36bf1e`, 최종 소스 커밋은 `b14d7c3247ee1aafdeafec6191767959fe942f82`, 배포·태그 커밋은 `f88636c058a6282daa3565d6ceadedfae2bd3bba`입니다. 빌드 이후 차이는 `docs/releases/v1.0.14.md`와 `docs/releases/v1.0.16/AGENTS.md` 두 문서뿐입니다. 배포 커밋의 단일 부모는 최종 소스이며 `.release/`의 해당 버전 세 파일 추가만 포함합니다. 원격 annotated 태그 역참조도 같은 배포 커밋입니다.

[게시 Actions](https://github.com/poyal/perilog/actions/runs/38139226593)가 성공했고 재빌드 없이 검증한 파일을 게시했습니다. 공개 시각은 `2026-10-11T12:26:08Z`, 공개 파일 확인 완료는 `2026-10-11T12:27:07.230893+00:00`입니다. 태그 푸시 시작부터 공개 파일 확인까지 66.7초, 게시 작업은 17초 걸렸습니다. main과 태그만 한 번 푸시하고 release 브랜치는 원격에 푸시하지 않았습니다.

공개 APK·화면 ZIP·체크섬 세 파일의 크기·SHA-256·GitHub digest·로컬 원본 바이트가 모두 일치했습니다. APK 앱 ID·버전 1.0.14·코드 15·최소 API 31·대상 API 36·기존 인증서, ZIP의 PNG 88장 전체 원본 바이트·CRC와 체크섬 두 항목을 확인했습니다. 최신 정식 릴리즈는 v1.0.14이며 초안·사전 공개가 아닙니다. 원격 Android 검증 워크플로의 수동 전용 설정과 이번 main·태그 푸시로 자동 검사가 생성되지 않은 것도 확인했습니다.

게시 직전 공개 API·원격 Git·Actions에서 동일 버전과 동시 게시를 확인했습니다. 공개 API로 보이지 않는 초안·기존 자산은 인증된 게시 스크립트가 해시와 재다운로드 바이트를 비교한 뒤 공개합니다. 근거는 `commit-relationship.json`, `publication-preflight.json`, `publish-push.log`·JSON, `publication-run.json`, `public-release.json`, `public-verification.json`, `public-apk-signature.txt`, `public-apk-manifest.txt`, `remote-tag-verification.json`, `remote-test-workflow.yml`입니다. 게시 확인 결과만 main의 후속 문서 커밋으로 남기며 공개 태그와 산출물은 유지합니다.

게시 기록 도구의 첫 원격 워크플로 비교는 Windows 작업 사본의 CRLF와 원격 LF 차이로 중단됐습니다. 원격 파일을 해당 소스 커밋의 Git blob과 바이트 비교하고, 작업 사본은 줄바꿈을 정규화해 비교하여 동일함을 확인했습니다. 공개 산출물 검증은 처음부터 통과했으며 파일을 재게시하거나 변경하지 않았습니다. 근거는 `workflow-line-endings.json`과 보완 전 기록 도구입니다.

사용자 확인용 에뮬레이터는 연결 종료를 확인해 데이터 초기화 없이 다시 열었습니다. 최종 개발 APK·검사 APK를 덮어 설치하고 `PreviewSamples#load`를 실행해 품목 7개·사용 구성 2개·기록 28건, 추가 기록·초안 0건과 기존 입력·수정값·초안 보존을 확인했습니다. 앱을 다시 열고 조작 가능한 창을 남겼습니다. 근거는 `preview-samples.log`·JSON과 `preview-emulator.log`입니다.

버전 `1.0.14` · 코드 `15` · 앱 ID `com.poyal.perilog` · DB 스키마 `9`. 날짜 선택을 **어제 → 오늘 → 날짜 선택** 순서로 정리하고 어제·오늘에 공통 버튼 배경·테두리와 선택 강조를 적용했습니다. 새 기록은 오늘로 시작하며 기존 기록·초안과 선택한 날짜는 유지합니다. 화면을 자정 이전부터 열어 두었어도 버튼을 누르는 시점의 오늘·어제를 계산합니다.

실행 ID `20261011T115221Z-v1.0.14-ae6a603`, 작업 폴더 `C:/Users/ttt45/Workspace/personal/perilog`, 담당자는 이번 Codex 세션입니다. 깨끗한 트리에서 빌드한 커밋은 `ae6a6038d81af3dd560a961ede28654a8c36bf1e`입니다. 최종 후보 검증은 `2026-10-11T11:52:21.723984+00:00`부터 `2026-10-11T12:25:12.894905+00:00`까지 32분 51초, 최초 준비부터는 57분 0초입니다. 가상 자료와 검증 전용 에뮬레이터만 사용했습니다.

| 검사 | 이번 최종 후보 결과 |
| --- | --- |
| 전체 단위·저장소 | 195개, 실패·오류·생략 0. Room API 31·35 포함. 예상 이름과 XML 일치 |
| Lint·빌드 | Debug·Release 오류 0, 경고 각각 39·40개. clean 후 개발·검사·기존 키 서명 APK 빌드 성공 |
| API 35 전체 UI | 기본·1.5배 글씨 각각 84개, 실패·오류·생략·이름 누락 0. 각각 454.2초·457.0초 |
| API 36 지정 회귀 | 기본·1.5배 각각 5개, 실패·오류·생략·이름 누락 0. 날짜 변경·어제 기록·미래 날짜 거부·달력·입력 초안·회전·키보드 확인 |
| 실제 프로세스 복구 | `PASS: process 12792 -> 13015; unsaved product form restored, then discarded.` |
| 최종 서명 APK 신규 설치 | API 35에서 62.3kg 저장·재시작 후 유지, 재고·설정 진입, 스키마 9 확인 |
| 공개 1.0.13 → 최종 APK | 삭제 없이 덮어 설치. 스키마 9→9, 19개 테이블·37행·341개 필드 값·모든 ID 동일 |
| 백업·권한 | UID·최초 설치 시각·전체 JSON·재고·초안·DataStore 기존 키·보호 백업·알림·실제 SAF 권한 유지. 폴더 재선택 없이 백업 1→2개 생성, 기존 파일 바이트 보존 |
| 설치 파일 일치 | 신규 설치와 덮어 설치의 실제 `base.apk`를 읽어 고정 APK 바이트·SHA-256과 일치 확인 |
| 안내·문서·산출물 | 안내 PNG 50장 원본 일치. 문서 로컬 링크 397개 확인. 매뉴얼이 사용하는 PNG 88장 ZIP CRC·원본 일치, 체크섬 정확히 두 항목 |

실제 빌드 명령은 `gradlew.bat clean :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease -PcaptureScreenshots --no-daemon --console=plain`입니다. Windows에서 버튼 검사는 설치된 Python 3.13.7로 `scripts/check-ui-buttons.py`를 별도 실행했습니다. UI는 로컬 보조 도구 `release-v108-ui-runner.py`에 현재 소스의 대상 목록과 기본/큰 글씨 프로필을 전달했습니다. 파일명의 과거 버전은 도구 이름이며 결과를 재사용한 것이 아닙니다. API 35 대상은 현재 `scripts/test-e2e.sh all`의 전체 12개 클래스·84개 메서드와 같습니다. 공식 `scripts/test-process-restore.py`, 로컬 `release-v114-signed-check.py`, `verify-v114-installed.py`도 실제 실행했고 단계별 로그·명령·종료 코드와 XML/HTML을 보관했습니다.

API 36은 이번 날짜 UI 변경과 키보드에 관련된 5개 메서드를 시작 전에 지정했습니다. 지난 버전의 29개 범위를 전체 통과했다고 표시하지 않습니다. 최소 SDK·OS 처리·DB 마이그레이션·잠금·업데이트 기능·게시 스크립트는 변경하지 않아 실제 API 31 추가 설치, 잠금 전용 조합, 다운로드 통합, 게시 코드 단위 검사·actionlint는 이번 추가 범위에서 제외했습니다. Room API 31 검사는 실제 Android 12 설치 결과를 대신하지 않습니다.

구버전 첫 실행의 알림 안내가 표시된 동안 로컬 조작 도구가 설정 버튼을 찾지 못해 가져오기 준비가 중단됐습니다. `signed-import.log`·JSON과 `failure-import.png`·XML, 보완 전 도구를 보존했습니다. 설정 컨트롤이 준비될 때까지 기다리도록 도구만 보완한 뒤 실제 가져오기부터 다시 진행했습니다. 이 중단을 통과에 합산하지 않았고 앱 소스·APK는 변경하지 않았습니다.

업데이트 자료는 공개 구버전의 실제 파일 선택기로 가져왔습니다. 필수 9개 테이블 모두 비어 있지 않으며 기계·추가투석, 음수·0·미입력, mL·g·kg, 과거 계산 기준, 취소 이력, 삭제 기록의 사용, 비활성 품목, 미저장·수정 초안과 비기본 설정을 포함합니다. 전후 DB 무결성은 `ok`, 재고 수량은 `37·0·-2·22`로 동일합니다. 전체 JSON은 `exportedAt`만 제외해 같고 DataStore는 `appUpdate`의 조회 메타데이터 변화만 허용했습니다. 알림 허용·실제 SAF `persisted=0x3`, 내부 보호 백업과 외부 백업의 기존 바이트도 보존됐습니다.

Lint 경고는 앱 언어 설정, 의존성·AGP 최신 버전 안내, Compose Modifier 인자, Drawable 호환, API 조건, 위젯 중첩 weight·작은 글씨, KTX 및 Release 단색 런처 아이콘입니다. 항목별 목록은 두 Lint XML과 `build-verification.json`에 있습니다.

| 배포 파일 | 바이트 | SHA-256 |
| --- | ---: | --- |
| `perilog-1.0.14.apk` | 19,540,435 | `fde62965c620162202fb242c5e49eaf9c120bba42fdc7237bf072607a2cb0f0d` |
| `perilog-1.0.14-screenshots.zip` | 15,166,863 | `42cb793b48bef18388335bc7b890878c1ceb22f01b8aa439069a3b063c7f33f8` |
| `perilog-1.0.14.sha256` | 182 | `7b8c5672c7ab4317d00b7fdc17b605672fe394db399509884d70daa639a4ba70` |

신규·덮어 설치는 고정한 `dist/perilog-1.0.14.apk`를 사용했습니다. 기존 공개와 같은 인증서 SHA-256은 `79adbeead4f7fbc2a5bf99219ee2fdd909f14eb18817c2b4f6139edbce0937dd`입니다. 직전 공개 1.0.13 APK의 SHA-256은 `9980a6f305365af1af11c79e55457d41f4be424dba17589baa201d6a65c59873`이며 공개 체크섬·인증서도 확인했습니다. 키·비밀번호·키 백업과 시험 DB는 Git이나 세 배포 파일에 포함하지 않습니다.

환경은 Windows 11 AMD64, JDK 17.0.20.1, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, SDK/Build Tools 36.0.0입니다. API 35 x86_64 `Perilog_Changes / emulator-5560`, API 36 x86_64 `Perilog_Release36 / emulator-5562`를 사용했고 글씨 배율은 1.0·1.5입니다. Gradle·ADB 검사·게시 단계는 순차 실행했습니다. 사용자 조작용 `Perilog_Test / emulator-5554`는 초기화나 자동 검사 대상으로 사용하지 않았습니다.

근거는 `.tools/release-checks/20261011T115221Z-v1.0.14-ae6a603/`의 `run.json`, `verification.md`, `candidate-build.log`, `unit-results/`, `build-verification.json`, `lint-debug.xml`, `lint-release.xml`, `api35-normal/`, `api35-large/`, `api36-normal/`, `api36-large/`, `process-evidence/`, `fresh-install.json`, `upgrade-before/`, `upgrade-after/`, `upgrade-comparison.json`, `automatic-backup-preservation.json`, `installed-apk-identity.json`, `results-summary.json`, `artifact-manifest.json`, `report-hashes.json`입니다. 변경 버튼의 밝게·어둡게/기본·큰 글씨·좁은 화면 확인 자료는 `.tools/date-buttons-visual/`에 있습니다.

원격 Android 진단은 미실행·불필요이며 수동 전용을 유지합니다. 통합 검증 명령·검증 기록 자동 차단·작업 폴더 간 잠금은 미구현이므로 로컬 도구와 보고서를 직접 대조했습니다. Galaxy Z 플립7 실물의 접기·펼치기·삼성 키보드·파일 선택기·생체 인증·장시간 절전, Google 실제 OAuth·웹 반영은 별도 확인 범위입니다.

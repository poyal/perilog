# v1.0.16 최종 검증 결과

[사용자 매뉴얼](README.md) · [GitHub Release](https://github.com/poyal/perilog/releases/tag/v1.0.16) · [공통 개발 규칙](../../development.md) · [배포 절차](../../release.md)

- 공개 태그 커밋: `229060a4bbb5719de271a67765a45d42e7d9d071`
- 아래 결과는 기존 최종 검증 기록을 정리한 것이며 이번 문서 정리에서 재실행한 결과가 아닙니다. 작업 로그·중간 후보·로컬 설치 파일은 보관하지 않습니다.

## 2026-10-18 v1.0.16 릴리즈 검증·게시 완료

[v1.0.16 공개 릴리즈](https://github.com/poyal/perilog/releases/tag/v1.0.16) 게시와 공개 파일 재검증을 완료했습니다. 빌드 커밋은 `d92df2a4d67b4cbb461a888882c2721f3a7292de`, 최종 소스 커밋은 `322ed4fa04de6096477ee408fb62f9b164e1e39c`, 배포·태그 커밋은 `229060a4bbb5719de271a67765a45d42e7d9d071`입니다. 빌드 이후 차이는 `docs/releases/v1.0.16.md`와 `docs/releases/v1.0.16/AGENTS.md` 두 문서뿐입니다. 배포 커밋의 단일 부모는 최종 소스이며 `.release/`의 해당 버전 세 파일 추가만 포함합니다. 원격 annotated 태그 역참조도 같은 배포 커밋입니다.

[게시 Actions](https://github.com/poyal/perilog/actions/runs/39105895223)가 성공했고 재빌드 없이 검증한 파일을 게시했습니다. 공개 시각은 `2026-10-18T06:58:04Z`, 공개 파일 확인 완료는 `2026-10-18T07:00:56.843729+00:00`입니다. 태그 푸시 시작부터 공개 파일 확인까지 200.7초, 게시 작업은 20초 걸렸습니다. main과 태그를 한 번의 원자적 푸시로 반영하고 release 브랜치는 원격에 푸시하지 않았습니다.

공개 APK·화면 ZIP·체크섬 세 파일의 크기·SHA-256·GitHub digest·로컬 원본 바이트가 모두 일치했습니다. APK 앱 ID·버전 1.0.16·코드 17·최소 API 30·대상 API 36·기존 인증서, ZIP의 PNG 89장 전체 원본 바이트·CRC와 체크섬 두 항목을 확인했습니다. 최신 정식 릴리즈는 v1.0.16이며 초안·사전 공개가 아닙니다. 원격 Android 검증 워크플로의 수동 전용 설정과 이번 main·태그 푸시로 자동 검사가 생성되지 않은 것도 확인했습니다.

게시 직전 공개 API·원격 Git·Actions에서 동일 버전과 동시 게시를 확인했습니다. 공개 API로 보이지 않는 초안·기존 자산은 인증된 게시 스크립트가 해시와 재다운로드 바이트를 비교한 뒤 공개합니다. 근거는 `commit-relationship.json`, `publication-preflight.json`, `publish-push.log`·JSON, `publication-run.json`, `public-release.json`, `public-verification.json`, `public-apk-signature.txt`, `public-apk-manifest.txt`, `remote-tag-verification.json`, `remote-test-workflow.yml`입니다. 게시 확인 결과만 main의 후속 문서 커밋으로 남기며 공개 태그와 산출물은 유지합니다.

버전 **1.0.16 · 코드 17 · 최소 API 30 · 대상 API 36 · DB 스키마 9**입니다. 평균저류시간을 시간(0~23)·분(0~59) 직접 입력으로 바꾸고, 잘못된 값 차단·각 칸 지우기·미입력과 0분 구분을 반영했습니다. 사용자 매뉴얼·앱 도움말과 관련 화면도 갱신했습니다.

실행 ID `20261018T062825Z-v1.0.16-d92df2a`, 담당 작업 폴더 `C:/Users/ttt45/Workspace/personal/perilog`, 담당자는 이번 Codex 세션입니다. 깨끗한 작업 트리의 빌드 커밋은 `d92df2a4d67b4cbb461a888882c2721f3a7292de`입니다. 검증 시작 `2026-10-18T06:28:25.786817+00:00`, 종료 `2026-10-18T06:57:11.506361+00:00`, 소요 시간 28분 45초입니다. 가상 자료와 검사 전용 에뮬레이터만 사용했습니다. 이전 실패 후보를 포함한 전체 로컬 작업 시간은 36분 51초입니다.

| 검사 | 실제 결과 |
| --- | --- |
| 전체 단위·저장소 | 250개, 실패·오류·생략 0. Room API 30·31·35 포함. 예상 메서드·API 실행 수를 XML과 대조 |
| 빌드·Lint | clean 후 Debug·AndroidTest·기존 키 서명 Release 빌드 성공. Debug/Release 오류 0, 경고 35·36개. 항목별 내역은 `build-verification.json`과 Lint XML |
| API 35 전체 UI | 12개 클래스의 모든 검사, 기본·1.5배 각각 101개 통과. 실패·생략·누락 0. 555.1초·560.7초 |
| API 36 입력 회귀 | 기본·1.5배 각각 7개 통과, 실패·생략·누락 0. 실제 키보드·회전·직접 시간 입력·도움말·미저장 확인·위젯 이동 |
| 실제 프로세스 복구 | `PASS: process 8987 -> 9322; unsaved product form restored, then discarded.` |
| 서명 APK 신규 설치 | API 35에서 62.3kg 기록 저장·프로세스 재시작 후 유지, 재고·설정 진입, DB 스키마 9 확인 |
| 공개 1.0.15 → 후보 덮어 설치 | API 35, 앱 삭제 없이 업데이트. 19개 테이블·37행·341개 기존 필드와 모든 ID 보존, DB 무결성 ok |
| 백업·권한 | UID·최초 설치 시각·재고·전체 JSON·초안·DataStore·보호 백업·알림·SAF 폴더 권한 보존. 같은 폴더 백업 1→2개, 기존 파일 바이트 유지 |
| 최종 설치 파일·정렬 | 신규·업데이트 후 base.apk가 고정 APK와 바이트 일치. ZIP·64비트 라이브러리 4개 16KB 정적 정렬 통과. 실제 16KB OS 실행 검사는 별도 |
| 문서·산출물 | 로컬 링크 413개 확인, 앱 안내 이미지 52장 원본 일치, 화면 ZIP 89장 CRC·원본 일치(현재 안내에서 빠진 시계판 93번 제외), 체크섬 정확히 두 항목 |

빌드 명령은 `gradlew.bat --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease -PcaptureScreenshots --offline --console=plain`입니다. Windows에서는 실행 자료에 보관한 `release-v116-ui-runner.py`로 `scripts/test-e2e.sh`의 12개 클래스 전체를 실행하고 XML·HTML·JSON에 실제 목록을 남겼습니다. 직전 전체 UI 100개에서 입력 복원 검사 1개가 추가되어 101개가 됐습니다. API 36은 시작 전에 지정한 7개 회귀 범위입니다. 프로세스 검사는 실제 `scripts/test-process-restore.py`를 실행했습니다.

구버전 가상 자료는 실제 Android 파일 선택기로 가져왔으며 필수 9개 테이블이 모두 비어 있지 않음을 확인했습니다. 기계·추가투석, 음수·0·미입력, mL·g·kg, 과거 계산 기준, 취소 이력, 삭제 기록의 사용 내역, 비활성 품목, 미저장·수정 중 초안, 비기본 설정과 이전 범위 초과 평균저류시간을 포함했습니다. 전체 JSON은 exportedAt만 제외하고 비교했으며, DataStore는 appUpdate 조회 메타데이터 변화만 허용했습니다. 재고 37·0·-2·22와 SAF persisted=0x3를 확인했습니다.

| 배포 파일 | 바이트 | SHA-256 |
| --- | ---: | --- |
| `perilog-1.0.16.apk` | 20,068,015 | `eaf05eafba6833cbf365db50ed0c6b72485155631753e941227b73920bd2bb1a` |
| `perilog-1.0.16-screenshots.zip` | 15,261,277 | `0fd16fc778d945eb87e3e6f7034f71e7b623d6f24b7a6a8d4a52cf36d1e74c24` |
| `perilog-1.0.16.sha256` | 182 | `b577264747f6a9de59ffb381e2cab4effe9dfa3f5f471586857b68cd66f470b0` |

APK는 `dist/perilog-1.0.16.apk`로 고정했습니다. 직전 [공개 1.0.15 APK](https://github.com/poyal/perilog/releases/download/v1.0.15/perilog-1.0.15.apk)의 SHA-256은 `85f50cface3b7e50bf5a94ffdda1ce082a323ce763c321ca82e0f02fd6f7be4e`이며 공개 체크섬·GitHub digest와 일치합니다. 공개 1.0.15 APK와 후보의 인증서 SHA-256은 `79adbeead4f7fbc2a5bf99219ee2fdd909f14eb18817c2b4f6139edbce0937dd`로 일치합니다. 세 배포 파일에 서명 키·비밀번호·키 백업·검사 DB를 포함하지 않습니다.

환경은 Windows AMD64, JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, SDK/Build Tools 36.0.0입니다. x86_64 검사 전용 `Perilog_Changes`(API 35/5560), `Perilog_Release36`(API 36/5562)를 사용했습니다. 최소 SDK·OS 호환 동작·의존성은 변경하지 않아 API 30·31의 실제 서명 설치를 이번 릴리즈에서 반복하지 않았습니다. Room API 30·31·35 단위 검사는 이번 후보에서 모두 수행했습니다. Galaxy Z 플립7 실물 접기·펼치기, 삼성 키보드·생체 인증·장시간 절전과 다른 OS/ABI 실기기 검사는 별도 미검증 범위입니다.

원격 Android 진단은 미실행·불필요로 수동 전용을 유지합니다. 통합 검증 명령·검증 기록 자동 차단·작업 폴더 간 잠금은 미구현이므로 실제 보고서·예상 목록·커밋 관계를 수동 대조했습니다. 검증 뒤 APK는 다시 빌드하지 않으며 게시 결과와 커밋 관계는 이어서 기록합니다.

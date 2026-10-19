# v1.0.7 최종 검증 결과

[사용자 매뉴얼](README.md) · [GitHub Release](https://github.com/poyal/perilog/releases/tag/v1.0.7) · [공통 개발 규칙](../../development.md) · [배포 절차](../../release.md)

- 공개 태그 커밋: `6d09e3ff7ba07bc66117611de15be205280c533c`
- 아래 결과는 기존 최종 검증 기록을 정리한 것이며 이번 문서 정리에서 재실행한 결과가 아닙니다. 작업 로그·중간 후보·로컬 설치 파일은 보관하지 않습니다.

## v1.0.7 — 게시와 공개 파일 검증 완료

2026-10-03. **버전 1.0.7 / 코드 8**, 빌드 커밋 `49c072d111940a728b6c986fbe94f28418e6b24a`의 깨끗한 작업 트리에서 전체 단위·Lint·Debug/Test/Release 빌드를 수행했습니다. 기존 키로 서명한 `dist/perilog-1.0.7.apk`를 고정하고 신규 설치·업데이트 검사에 같은 바이트를 사용했습니다.

| 검사 | 실제 결과 |
| --- | --- |
| 전체 단위·저장소 | 92개 통과. 소스의 69개 검사 이름과 API 31·35 실행을 대조, 실패·오류·생략·누락 0 |
| Lint | 오류 0, Debug 경고 5·Release 경고 6. SDK 조건 1·KTX 제안 2·Modifier 인자 순서 권고 1·기간 선택기 로케일의 AppBundleLocaleChanges 1, Release 단색 런처 아이콘 1. 단일 APK 배포이며 App Bundle은 미검사 |
| API 35 전체 UI | 기본 39개·1.5배 39개 모두 통과. AppFlowTest·AppointmentFlowTest·UiComponentsTest의 예상/실행 이름 일치, 실패·생략·누락 0 |
| API 36 입력 회귀 | 기본·1.5배 각각 3개 통과. 회전·키보드·입력 복구·미저장 재고 보존, 전화번호 공백/짧은 번호·중간 편집/삭제/커서 확인 |
| 실제 프로세스 종료·복구 | process 8038 -> 8247; unsaved product form restored, then discarded. |
| 최종 서명 APK 신규 설치 | API 36에서 62.3kg 기록 저장·DB 62300g 확인, 재고·설정 진입. 가로·1.5배 키보드 화면도 확인 |
| 공개 v1.0.6 → v1.0.7 | 삭제 없이 덮어 설치. DB 7·무결성 ok, 14개 테이블·36행·337개 기존 필드 전부 일치 |
| 재고·권한·백업 | 37 / 0 / −2 / 22EA 유지. 앱 UID·최초 설치 시각·알림·폴더 권한·DataStore 기존 키·내부 보호 백업 유지. 전체 내보내기 값 일치, 기존 백업 바이트 보존과 같은 폴더의 신규 백업 확인 |
| 화면·문서 | 촬영 검사 3개 통과. 현재 화면 PNG 45장, API 35 개발 빌드 43장·API 36 서명 APK 2장. ZIP에서 과거 통계 표 이미지를 제외하고 매뉴얼과 원본 대조 |

통계 도표의 양수·음수·0·미입력, 같은 날짜의 복수 기록, 기록표 1,000행·핀치·고정 열·선택 상세·회전·편집 복귀, 기간 취소·적용·직접 날짜 입력·기간 지정의 버튼 활성 조건, 연락처 자동 하이픈과 짧은 번호·재생성·연속 등록을 포함했습니다. 공통 버튼의 흰색 배경·모서리·높이와 다크 모드도 실제 촬영 화면으로 확인했습니다.

업데이트 자료는 가상 자료만 사용했습니다. 14개 테이블 모두에 기존 ID·메모·값을 넣고 기계/추가투석, 음수·0·미입력, mL/g/kg, 과거 계산 기준, 취소 이력, 비활성 품목, 삭제 기록에 연결됐던 사용, 미저장·수정 초안, 예전 시작/종료 시각, 연락처·병원 일정·비기본 설정을 보존하는지 비교했습니다. 사용자용 `Perilog_Test / emulator-5554`는 초기화하거나 자동 검사에 사용하지 않았습니다.

Windows 11 x64, JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, Build Tools 36.0.0. API 35 x86_64 `Perilog_Changes / emulator-5560`, API 36 x86_64 `Perilog_Release36 / emulator-5562`에서 단일 작업 폴더의 검사를 순차 실행했습니다. 전체 빌드는 2분 51초, 로컬 검사·문서·산출물 준비는 20.5분입니다.

직접 ADB instrumentation과 로컬 보조 스크립트로 원본 로그·JUnit XML·HTML·예상 이름을 대조했습니다. 통합 릴리즈 명령이나 자동 게시 전 검증 차단 기능을 구현한 것으로 간주하지 않습니다. 원격 Android 진단은 미실행·불필요입니다. 업데이트/게시 코드·최소 SDK 변경이 없어 해당 전용 회귀와 API 31 실제 설치는 추가하지 않았습니다. Galaxy Z 플립7 실물의 접기·펼치기, 삼성 키보드·파일 선택기·생체 인증·장시간 절전은 별도 확인 범위입니다.

APK 6,016,038바이트, SHA-256 `461665dd5469398b959f9d9b2bb81c2ccd02ad680915e193cf65b6b2d3f2246c`. 직전 공개 APK와 인증서 `79adbeead4f7fbc2a5bf99219ee2fdd909f14eb18817c2b4f6139edbce0937dd`가 일치합니다. ZIP 5,505,376바이트, SHA-256 `f50f770a91c828c20b452fa8ce38e6f3f698a27df30f94c13b34d42548e32635`. 체크섬은 APK·ZIP 두 항목이며 PNG 45장의 CRC·원본 바이트와 압축 무결성을 확인했습니다. 문서 상대 링크 228개와 README 목차 앵커도 확인했습니다.

근거: `.tools/release-checks/20261003T101649Z-v1.0.7-93604a3/`. `candidate-build.log`, `build-verification.json`, `unit-results/`, `lint-debug.xml`, `lint-release.xml`, `api35-normal/`, `api35-large/`, `api36-normal/`, `api36-large/`, `process-evidence/`, `capture/`, `fresh-install.json`, `upgrade-comparison.json`, `automatic-backup-preservation.json`, `artifact-manifest.json`에 해당 실행 결과를 보관했습니다. 실행 ID의 끝 해시는 준비 시점이며 실제 빌드 출처는 위 커밋입니다.

### v1.0.7 게시 출처와 공개 확인

| 출처 | 확인 |
| --- | --- |
| buildCommit | `49c072d111940a728b6c986fbe94f28418e6b24a` |
| sourceCommit | `4db789b5676ec7cc8a3663c56d0e3ea10525fc09` |
| build → source | 사용자 문서·릴리즈 노트·검증 기록과 번호 PNG만 50개 경로 변경. 앱·Gradle·검사·촬영 코드 변경 없음 |
| releaseCommit | `6d09e3ff7ba07bc66117611de15be205280c533c`. 단일 부모는 sourceCommit이며 `.release/`의 해당 버전 세 파일만 추가 |
| 태그 | annotated `v1.0.7`이 releaseCommit을 가리킴. 원격 태그 역참조도 동일 |
| 푸시 | main과 태그를 한 번에 푸시. release 브랜치 중복 푸시 없음 |

[게시 작업 37117083281](https://github.com/poyal/perilog/actions/runs/37117083281)이 성공했습니다. 기존 서명 APK를 재빌드 없이 게시했으며 작업은 18초 걸렸습니다. 실행 목록에서 main·태그 푸시로 무거운 Android 검증이 생성되지 않은 것도 확인했습니다. 게시 전 공개 API와 원격 refs에서 같은 버전·진행 중 게시가 없음을 확인했고, 게시 스크립트의 인증된 GitHub 경로에서 초안/기존 자산을 검사한 뒤 공개했습니다.

2026-10-03T10:39:27.120898+00:00에 [v1.0.7 공개 릴리즈](https://github.com/poyal/perilog/releases/tag/v1.0.7)가 최신 정식 릴리즈이고 초안·사전 공개가 아닌 것을 확인했습니다. 공개 URL에서 세 파일을 다시 내려받아 로컬 원본·Git blob·GitHub 자산 digest와 바이트가 일치함을 확인했습니다. APK 서명·앱 ID·버전 1.0.7/코드 8, ZIP의 원본 PNG 45장과 압축 무결성도 통과했습니다. 태그 푸시 시작부터 공개 재다운로드 확인까지 64.0초 걸렸습니다.

- `perilog-1.0.7-screenshots.zip`: 5,505,376바이트, SHA-256 `f50f770a91c828c20b452fa8ce38e6f3f698a27df30f94c13b34d42548e32635`.
- `perilog-1.0.7.apk`: 6,016,038바이트, SHA-256 `461665dd5469398b959f9d9b2bb81c2ccd02ad680915e193cf65b6b2d3f2246c`.
- `perilog-1.0.7.sha256`: 180바이트, SHA-256 `315db28d66857f180f8027bb030269b7dbfcb5ce479546c8299a3f8eab8a5f2a`.

최종 근거는 실행 폴더의 `commit-relationship.json`, `publication-run.json`, `public-release.json`, `public-verification.json`, `public-apk-signature.txt`, `public-apk-manifest.txt`, `public/`에 보관했습니다. 게시 확인 문서만 main의 후속 커밋으로 남기며 이미 공개한 태그와 APK는 유지합니다.

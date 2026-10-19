# v1.0.8 최종 검증 결과

[사용자 매뉴얼](README.md) · [GitHub Release](https://github.com/poyal/perilog/releases/tag/v1.0.8) · [공통 개발 규칙](../../development.md) · [배포 절차](../../release.md)

- 공개 태그 커밋: `b4a3b11d436754e7c47f3a162a0442b26b76bc3e`
- 아래 결과는 기존 최종 검증 기록을 정리한 것이며 이번 문서 정리에서 재실행한 결과가 아닙니다. 작업 로그·중간 후보·로컬 설치 파일은 보관하지 않습니다.

## v1.0.8 — 게시와 공개 파일 검증 완료

2026-10-04. **버전 1.0.8 / 코드 9**, 빌드 커밋 `95d72b93a859cbaa15a5a24eed0b50740e10811b`의 깨끗한 작업 트리에서 전체 후보 검사를 수행했습니다. 기존 키로 서명한 `dist/perilog-1.0.8.apk`를 고정하고 설치·업데이트·게시에는 같은 바이트를 사용합니다. 아래 결과는 이번 후보의 실제 실행 결과입니다.

| 검사 | 실제 결과 |
| --- | --- |
| 전체 단위·저장소 | 133개 통과. 소스 104개 이름과 Room API 31·35 실행을 대조, 실패·오류·생략·누락 0 |
| Lint·서명 빌드 | Debug·Release 오류 0, 서명·앱 ID·1.0.8/코드 9 확인 |
| API 35 전체 UI | 기본 55개·1.5배 55개 통과. AppFlowTest·AppointmentFlowTest·UiComponentsTest·ReplenishmentFlowTest·WidgetFlowTest 전체 예상 이름 일치 |
| API 36 추가 회귀 | 기본·1.5배 각각 19개 통과. 입력·전화번호·계산기·오프라인 안내·연락처 순서·전체 위젯 검사 |
| API 31 실제 위젯 | WidgetFlowTest 10개 통과. 최소 지원 OS에서 실제 AppWidgetHostView·이동·초안·테마·크기 조절 확인 |
| 실제 프로세스 복구 | process 26129 -> 26345; unsaved product form restored, then discarded. |
| 최종 서명 APK 신규 설치 | API 36에서 62.3kg 기록 저장·DB 62300g 확인, 재고·설정 진입, 가로·1.5배 키보드 표시 확인 |
| 공개 v1.0.7 → v1.0.8 | 삭제 없이 덮어 설치. DB 7→8, 무결성 ok. 기존 14개 테이블·36행·337개 필드 값 보존 |
| 권한·재고·백업 | 재고 37 / 0 / −2 / 22EA, UID·최초 설치 시각·알림·SAF 폴더 권한·DataStore 기존 키·내부 보호 파일 유지. 같은 폴더에 재연결 없이 새 백업 생성 |
| 사용자 안내·문서 | 18개 주제·69단계 점검, 앱 참조 PNG 39장 원본 일치. 검색·이미지 확대·입력 복귀 UI 검사 통과. 현재 매뉴얼용 PNG 67장 ZIP 원본·CRC 확인 |

Lint 세부 결과: debug: 경고 37개 (AppBundleLocaleChanges 1, GradleDependency 11, NewerVersionAvailable 4, ModifierParameter 1, UseCompatTextViewDrawableXml 6, ObsoleteSdkInt 1, NestedWeights 4, SmallSp 7, UseKtx 2) / release: 경고 38개 (AppBundleLocaleChanges 1, GradleDependency 11, NewerVersionAvailable 4, ModifierParameter 1, UseCompatTextViewDrawableXml 6, ObsoleteSdkInt 1, NestedWeights 4, MonochromeLauncherIcon 1, SmallSp 7, UseKtx 2). 경고는 XML·HTML 원본에 보관했으며 오류로 처리된 항목은 없습니다. 의존성 갱신 제안·미리보기의 작은 글씨/중첩 weight·호환 drawable·KTX 권고 등을 기록했습니다. 위젯 미리보기는 작은 공간을 위한 값이며 실제 호스트 크기/잘림 검사를 별도로 수행했습니다. 앱 번들·런처 단색 아이콘 관련 경고도 남아 있습니다. 경고를 통과 개수에 포함하지 않았습니다.

최종 서명 APK도 API 35의 실제 Launcher에 두 위젯을 추가해 확인했습니다. 기록의 세 단계와 예약의 시간·처치가 표시됐고, 어제 영역은 표시된 날짜의 기록을, 일정 본문은 해당 예약 수정을 열었습니다. 실제 화면 패키지가 출시용 `com.poyal.perilog`임을 대조했습니다. 근거는 `signed-widget-check.json`, `signed-widget-home-appointment.png`, 두 연결 화면의 PNG·UI XML입니다.

첫 후보에서는 전화번호 테스트 하나가 이전의 직접 수정 버튼을 찾아 실패했습니다. 실패 로그는 `.tools/release-checks/20261003T170155Z-v1.0.8-14ec827/`에 보존했습니다. 현재 ⋮ 메뉴로 진입하도록 검사만 수정하고 단독 재검사 후 새 커밋에서 전체 후보 검사를 다시 수행했습니다. 이전 후보의 통과 결과로 대체하지 않았습니다. 서명 APK 신규 설치 보조 도구는 가로로 남아 있던 에뮬레이터에서 첫 입력란을 찾지 못해 중단했습니다. 화면·로그를 보존하고 세로/기본 글씨를 설정하며 입력란 스크롤을 보완한 뒤 동일 APK로 다시 검사했습니다. 앱 소스나 APK는 변경하지 않았습니다.

입고 계산의 기록 유무·주간 구성·직접 입력·잔량/요청량 수정·분할 입고·달력의 일곱 열, 위젯의 4×1·4×2·2×2·길쭉한 실제 크기·회전·긴 진료과·모든 처치·이전 치료 구성·라이트/다크·시스템 테마·초안 저장 후 이동, 연락처 순서 저장/취소·⋮ 메뉴를 포함했습니다. 원격 Android Actions 검사는 미실행·불필요이며 게시 작업에서 앱을 다시 빌드하지 않습니다.

업데이트 자료는 합성 자료만 사용했습니다. 기계/추가투석, 음수·0·미입력, mL/g/kg, 과거 기준, 취소 이력, 비활성 품목, 삭제 기록에 연결된 사용, 미저장/수정 초안, 연락처·예약·비기본 설정을 포함합니다. 신규 `replenishment_plans`는 빈 테이블, `receipts.requestPlanId`는 null을 확인했습니다. JSON은 같은 기존 필드를 보존하면서 `replenishmentPlans=[]`, `preferences.contactOrder=[]`, 입고의 `requestPlanId=null`만 추가했습니다. `exportedAt`과 DataStore의 업데이트 상태는 정상적인 변경으로 분리했습니다.

Windows x64, JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, Build Tools 36.0.0. 검증 전용 API 35 `Perilog_Changes / emulator-5560`, API 36 `Perilog_Release36 / emulator-5562`, API 31 `Perilog_Widget31 / emulator-5564`를 사용했습니다. 사용자용 에뮬레이터나 실제 데이터는 초기화·검사하지 않았습니다. 최종 후보 검증 실행은 34.9분이 걸렸습니다.

로컬 보조 스크립트에서 직접 ADB instrumentation 결과를 JUnit XML·HTML로 보관하고 예상 이름과 대조했습니다. 서명 APK의 화면 조작·DB/권한 비교도 로컬 보조 스크립트로 수행했습니다. 통합 검증/게시 명령과 게시 전 검증 차단은 구현되어 있지 않아 운영 가이드에 따라 결과·커밋·서명·파일을 직접 대조합니다. 업데이트/게시 코드 자체는 변경하지 않았으므로 해당 전용 회귀를 추가 실행하지 않았습니다. Galaxy Z 플립7 실물의 접기·펼치기, 삼성 홈·키보드·파일 선택기·생체 인증·장시간 절전은 별도 확인 범위입니다.

APK 17,439,403바이트, SHA-256 `4af849c4ca60507144b270395bf78d3a9aa0e0d67dc9dcd916537ac030715d01`. 직전 공개 APK와 인증서 `79adbeead4f7fbc2a5bf99219ee2fdd909f14eb18817c2b4f6139edbce0937dd` 일치. ZIP 12,308,521바이트, SHA-256 `d505439fa2db874e028e1efdc342cdce42e582fcfd46aebf60e3a27935881f0e`. 체크섬은 APK·ZIP 두 항목입니다. 상대 링크 316개와 README 앵커를 확인했습니다. 이전 위젯 57·58·61번과 과거 통계 표는 현재 매뉴얼 ZIP에서 제외했습니다. 화면 재사용·새 촬영 조건은 화면 안내에 명시했습니다.

근거: `.tools/release-checks/20261003T172140Z-v1.0.8-95d72b9/`의 `candidate-build.log`, `build-verification.json`, 단위 XML·Lint, 각 UI 폴더, `process-evidence/`, `fresh-install.json`, `expected-migration.json`, `upgrade-comparison.json`, `automatic-backup-preservation.json`, `guide-audit.json`, `artifact-manifest.json`. 실행 ID 끝의 해시는 준비 시점이며 빌드 출처는 위 커밋입니다.

### v1.0.8 게시 출처와 공개 확인

| 출처 | 확인 |
| --- | --- |
| buildCommit | `95d72b93a859cbaa15a5a24eed0b50740e10811b` |
| sourceCommit | `492935ae012030a86ac52ace7f9a2e5b773900f0` |
| build → source | 사용자 문서·릴리즈 노트·검증 기록과 번호 PNG만 4개 경로 변경. 앱·Gradle·검사·촬영 코드 변경 없음 |
| releaseCommit | `b4a3b11d436754e7c47f3a162a0442b26b76bc3e`. 단일 부모는 sourceCommit이며 `.release/`의 해당 버전 세 파일만 추가 |
| 태그 | annotated `v1.0.8`이 releaseCommit을 가리킴. 원격 태그 역참조도 동일 |
| 푸시 | main과 태그를 한 번에 푸시. release 브랜치 중복 푸시 없음 |

[게시 작업 37142670782](https://github.com/poyal/perilog/actions/runs/37142670782)이 성공했습니다. 기존 서명 APK를 재빌드 없이 게시했으며 작업은 17초 걸렸습니다. 실행 목록에서 main·태그 푸시로 무거운 Android 검증이 생성되지 않은 것도 확인했습니다. 게시 전 공개 API와 원격 refs에서 같은 버전·진행 중 게시가 없음을 확인했고, 게시 스크립트의 인증된 GitHub 경로에서 초안/기존 자산을 검사한 뒤 공개했습니다.

2026-10-03T18:02:31.713954+00:00에 [v1.0.8 공개 릴리즈](https://github.com/poyal/perilog/releases/tag/v1.0.8)가 최신 정식 릴리즈이고 초안·사전 공개가 아닌 것을 확인했습니다. 공개 URL에서 세 파일을 다시 내려받아 로컬 원본·Git blob·GitHub 자산 digest와 바이트가 일치함을 확인했습니다. APK 서명·앱 ID·버전 1.0.8/코드 9, ZIP의 원본 PNG 67장과 압축 무결성도 통과했습니다. 태그 푸시 시작부터 공개 재다운로드 확인까지 68.0초 걸렸습니다.

- `perilog-1.0.8-screenshots.zip`: 12,308,521바이트, SHA-256 `d505439fa2db874e028e1efdc342cdce42e582fcfd46aebf60e3a27935881f0e`.
- `perilog-1.0.8.apk`: 17,439,403바이트, SHA-256 `4af849c4ca60507144b270395bf78d3a9aa0e0d67dc9dcd916537ac030715d01`.
- `perilog-1.0.8.sha256`: 180바이트, SHA-256 `8676af3ea9a41ee775b7938c5b4d3d7a307f9bcafe22e3c0249adbff5d8d9a74`.

최종 근거는 실행 폴더의 `commit-relationship.json`, `publication-run.json`, `public-release.json`, `public-verification.json`, `public-apk-signature.txt`, `public-apk-manifest.txt`, `public/`에 보관했습니다. 게시 확인 문서만 main의 후속 커밋으로 남기며 이미 공개한 태그와 APK는 유지합니다.

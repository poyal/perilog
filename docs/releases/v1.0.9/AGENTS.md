# v1.0.9 최종 검증 결과

[사용자 매뉴얼](README.md) · [GitHub Release](https://github.com/poyal/perilog/releases/tag/v1.0.9) · [공통 개발 규칙](../../development.md) · [배포 절차](../../release.md)

- 공개 태그 커밋: `fbfed350ffc768f7a318689cd8f8ca9d46ff3d96`
- 아래 결과는 기존 최종 검증 기록을 정리한 것이며 이번 문서 정리에서 재실행한 결과가 아닙니다. 작업 로그·중간 후보·로컬 설치 파일은 보관하지 않습니다.

## 2026-10-04 v1.0.9 릴리즈 검증·게시 완료

[v1.0.9 공개 릴리즈](https://github.com/poyal/perilog/releases/tag/v1.0.9) 게시와 공개 파일 재검증을 완료했습니다. 빌드 커밋은 `bad32efcaa27edf82846475ac4fb87e669e4f2ab`, 최종 소스 커밋은 `58328786594dce4d7c2f2a700d426dfe724b2d4a`, 배포·태그 커밋은 `fbfed350ffc768f7a318689cd8f8ca9d46ff3d96`입니다. 빌드 후 소스 차이는 `docs/releases/v1.0.9.md`와 `docs/releases/v1.0.16/AGENTS.md` 두 문서뿐입니다. 배포 커밋은 최종 소스의 단일 자식이며 해당 버전의 `.release/` 세 파일 추가만 포함합니다. 원격 annotated 태그의 대상도 일치합니다.

[게시 Actions](https://github.com/poyal/perilog/actions/runs/37206478500)는 18초에 성공했으며 APK를 재빌드하지 않았습니다. 게시 시작부터 공개 재다운로드 확인까지 1분 25초입니다. 세 공개 파일의 크기·SHA-256·GitHub 자산 digest·로컬 바이트가 모두 같고, 공개 APK의 기존 인증서·앱 ID·코드 10·버전 1.0.9와 ZIP 72개 PNG의 원본 일치·CRC를 재확인했습니다. 최신 공개 릴리즈도 v1.0.9입니다. 공개 시각은 `2026-10-04T13:41:05Z`, 재다운로드 검증 완료는 `2026-10-04T13:42:03.245290+00:00`입니다. 원격 소스의 Android 검사는 `workflow_dispatch`만 유지하며 이번 푸시로 무거운 Android 자동 검사가 생성되지 않았습니다. 근거: `commit-relationship.json`, `public-status.json`, `public-verification.json`, `public-apk-signature.txt`, `public-apk-manifest.txt`, `remote-test-workflow.yml`.

버전 `1.0.9` · 코드 `10` · 앱 ID `com.poyal.perilog`. 빌드 커밋은 `bad32efcaa27edf82846475ac4fb87e669e4f2ab`이며 깨끗한 작업 트리에서 빌드했습니다. 가상 자료만 사용했고, 사용자 수동 테스트용 `Perilog_Preview36 / emulator-5554`는 초기화하지 않았습니다. 실행 ID는 `20261004T122517Z-v1.0.9-4fc5baf`이며 로컬 검증 소요 시간은 74분 14초입니다.

| 검사 | 최종 후보 결과 |
| --- | --- |
| 단위·저장소 | 전체 143개, 실패·오류·생략 0. 소스 113개 이름과 Room API 31·35 반복 실행 대조 |
| Lint·빌드 | Debug·Release 오류 0, 경고 38·39개. Debug·검사용·최적화 서명 Release 빌드 성공 |
| API 35 전체 UI | 기본·1.5배 각각 59개. 실패·오류·생략·예상 이름 누락 0 |
| API 36 지정 회귀 | 기본·1.5배 각각 13개. 키보드·표·입고 요청·잔량 예측 포함, 실패·오류·생략·이름 누락 0 |
| 실제 프로세스 복구 | API 35 실제 PID 종료 후 새 프로세스에서 미저장 품목 복구·버리기 확인 |
| 최종 APK 신규 설치 | API 36에서 몸무게 62.3kg 기록 저장, 재고·설정 진입, DB 무결성 확인 |
| 공개 v1.0.8 → 최종 APK | API 35에서 삭제 없이 업데이트. 스키마 8 · 15개 테이블 · 35행 · 309개 기존 필드 값 동일 |
| 자동·보호 백업 | 기존 보호 백업 1개 바이트 일치. 폴더 재선택 없이 같은 폴더에 새 자동 백업 생성·기존 파일 보존 |
| 최적화 APK 위젯 | API 31·35·36 혼합 3개·일정 단독, 실제 PID 종료 각 4회, 시각 방송·재부팅 통과. API 35는 화면 꺼짐 180초, 31·36은 각 10초 |
| 앱 안내·문서·ZIP | 안내 18주제·73단계, 번들 PNG 43장 원본 일치. 매뉴얼 PNG 72장 ZIP의 목록·바이트·CRC 확인 |

API 35의 최초 전체 검사는 기본 59개 통과, 큰 글씨의 위젯 터치 1개 실패였습니다. Android 전환 중 시스템 InputSink에 터치가 전달된 로그를 확인하고 위젯 창의 포커스와 입력 대기를 추가했습니다. 다음 후보는 기본 59개 통과, 큰 글씨의 날짜 가로 회전 검사 1개 실패였습니다. 설정의 방향 값이 바뀐 직후 새 다이얼로그 배치 전에 검사한 문제로, 가로 창·복원된 입력칸 표시와 입력값을 기다리도록 보완했습니다. 두 경우 모두 집중 검사 기본·큰 글씨 통과 후 최종 후보 전체를 다시 검사했습니다. 이전 실패 보고서를 보존했으며 부분 재실행 결과를 전체 통과로 취급하지 않았습니다.

앱 기능·단위 검사 소스는 최초 빌드 `45ddd1a161b27ae17bbe4840771d57f21c5df614`와 동일합니다. 이후 검사 코드만 보완했으며 Gradle의 변경 없는 단위·Lint 결과 재사용을 기록했습니다. 두 후보 교체 모두 APK 압축 항목 차이는 `META-INF/version-control-info.textproto`의 커밋 정보뿐임을 확인했습니다. 새 최종 APK로 설치·업데이트·최적화 위젯 검사를 수행했습니다.

업데이트 자료는 음수·0·미입력, 기계·추가투석의 mL·g·kg, 과거 계산 기준, 취소 사용·입고, 비활성 품목, 삭제 기록의 사용 내역, 미저장·수정 초안, 비기본 설정, 진료과·치료 구성·예약·연락처, 기존 계산식의 입고 요청·부분 입고를 포함합니다. 재고 화면의 37·0·-2와 비활성 품목 22, UID·최초 설치 시각·알림 권한·SAF 지속 권한을 비교했습니다. 내보내기 JSON은 시각과 계획한 신규 기본 필드(`stockForecastPattern`, 요청 `calculationVersion=1`, `extraQuantities={}`)만 정규화했으며 기존 값이 일치합니다. DataStore는 `appUpdate`를 제외한 기존 키 값을 바이트로 비교했습니다. 최적화 APK에서도 홈 병원 일정 → 상세 → 예상 잔량 이동 후 DB 값이 그대로인지 확인했습니다. 구버전의 폴더 권한은 실제 연결·백업 성공으로 확인했고, 업데이트 후에는 `dumpsys activity permissions`의 `persisted=0x3`와 구버전 내보내기 이전의 권한 생성 시각을 확인했습니다. 최초 `dumpsys uri_grants` 출력은 이 기기에서 비어 있어 그 파일을 권한 근거로 사용하지 않았습니다. 일회성 UI 도구의 XML 선택·파일 선택기 경로·스크롤과 UID·권한 출력 파싱을 조정한 시도도 보존했습니다. 이 과정에서 앱 코드는 바꾸지 않았습니다.

도구: macOS arm64, Temurin JDK 17.0.20.1, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, SDK·Build Tools 36, minSdk 31. 실제 검사 기기는 arm64의 `Perilog_Changes35 / emulator-5560`, `Perilog_Changes36 / emulator-5562`, `Perilog_Changes31 / emulator-5564`입니다. Lint 경고는 의존성·AGP 갱신, AppBundleLocaleChanges, ModifierParameter, NestedWeights, SmallSp, UseCompatTextViewDrawableXml, UseKtx, ObsoleteSdkInt 및 Release의 MonochromeLauncherIcon입니다. 오류는 없습니다.

원격 Android 진단은 로컬로 검증 가능한 변경이라 실행하지 않았습니다. 릴리즈 검증 통합 자동화와 작업 폴더 간 잠금은 미구현이며 명령·보고서·커밋 관계는 수동 대조했습니다. 실물 Galaxy Z 플립7의 접기·삼성 키보드·파일 선택기·생체 인증·장시간 절전은 이번 검사 범위에 포함하지 않았습니다.

| 고정한 배포 파일 | 바이트 | SHA-256 |
| --- | ---: | --- |
| `perilog-1.0.9.apk` | 18,203,202 | `8adce2433fcddae756957af27bfbb92d0b67769bb55fb16d11934bbf82ad4003` |
| `perilog-1.0.9-screenshots.zip` | 13,084,667 | `f578f2758b151de47dd103e77ef9ef37369f8b88b7628c1b0a1f81c94ccad776` |
| `perilog-1.0.9.sha256` | 180 | `84d508def9c2ada29324dda4dc4cbd5be7ae461fd5e6568ea390cdbc98c05222` |

인증서 SHA-256은 공개 v1.0.8과 같은 `79adbeead4f7fbc2a5bf99219ee2fdd909f14eb18817c2b4f6139edbce0937dd`입니다. APK의 내장 VCS 커밋과 buildCommit을 대조했습니다. 서명 키·원본 키 백업·가상 DB는 공개 파일에 포함하지 않습니다.

원본 근거: `.tools/release-checks/20261004T122517Z-v1.0.9-4fc5baf/`의 `candidate-build-range-fixed.log`, `unit-results-final/`, `build-reports-final/`, `api35-final/`, `api36/`, `process/`, `upgrade-comparison.json`, `automatic-backup-preservation.json`, `fresh-install.json`, `signed-detail.json`, `widgets31/`, `widgets35/`, `widgets36/`, `artifact-manifest.json`, `verification.md`. 업데이트 UI 조작은 이 실행에만 저장한 `device-verification.py`를 사용한 수동 자동화이며 공통 공식 명령으로 간주하지 않습니다.

# v1.0.10 최종 검증 결과

[사용자 매뉴얼](README.md) · [GitHub Release](https://github.com/poyal/perilog/releases/tag/v1.0.10) · [공통 개발 규칙](../../development.md) · [배포 절차](../../release.md)

- 공개 태그 커밋: `8b6a079a714faca51a2e39014f33c22d14a2bd14`
- 아래 결과는 기존 최종 검증 기록을 정리한 것이며 이번 문서 정리에서 재실행한 결과가 아닙니다. 작업 로그·중간 후보·로컬 설치 파일은 보관하지 않습니다.

## 2026-10-06 v1.0.10 릴리즈 검증·게시 완료

[v1.0.10 공개 릴리즈](https://github.com/poyal/perilog/releases/tag/v1.0.10) 게시와 공개 파일 재검증을 완료했습니다. 빌드 커밋은 `a68a358149240597e2bd6529f00850b3ccea9983`, 최종 소스 커밋은 `cebda8449aca67cb30ad377ce1ed01c4a8ae5733`, 배포·태그 커밋은 `8b6a079a714faca51a2e39014f33c22d14a2bd14`입니다. 빌드 후 변경은 `docs/releases/v1.0.10.md`, `docs/releases/v1.0.16/AGENTS.md`, `docs/위젯-개발-가이드.md` 세 문서뿐입니다. 배포 커밋은 최종 소스의 단일 자식이며 해당 버전의 `.release/` 세 파일 추가만 포함합니다. 원격 annotated 태그 대상도 일치합니다.

[게시 Actions](https://github.com/poyal/perilog/actions/runs/37418995500)가 성공했으며 APK를 재빌드하지 않았습니다. 게시 시작부터 공개 재다운로드 검증까지 1분 35초입니다. 공개 시각은 `2026-10-06T05:32:14Z`, 검증 완료는 `2026-10-06T05:33:16.917794+00:00`입니다. 공개 APK·스크린샷 ZIP·체크섬 파일의 크기·SHA-256·GitHub 자산 digest·로컬 바이트가 모두 같고, 기존 인증서·앱 ID·코드 11·버전 1.0.10과 ZIP 80개 PNG의 원본 바이트·CRC도 확인했습니다. 최신 공개 릴리즈는 v1.0.10이며, 원격 Android 검사는 수동 진단 전용을 유지하고 이번 게시 커밋의 자동 Android 검사는 생성되지 않았습니다. 근거: `commit-relationship.json`, `public-status.json`, `public-verification.json`, `public-apk-signature.txt`, `public-apk-manifest.txt`, `remote-test-workflow.yml`.

수동 테스트용 `perilog_api36 / emulator-5554` 한 대를 새 창으로 열고 `-PcaptureScreenshots`로 빌드한 최신 개발·검사용 APK를 덮어 설치했습니다. `PreviewSamples#load` 1개가 통과했으며 기존 입력·수정값·초안을 보존했습니다. 품목 7개·구성 3개·기록 20건, 추가한 기록·초안은 각각 0건입니다. 준비 뒤 페리로그 앱을 다시 열었고 자동 검사 기기는 모두 종료했습니다. 근거: `manual-build.log`, `manual-samples.log`.

버전 `1.0.10` · 코드 `11` · 앱 ID `com.poyal.perilog` · 스키마 `8`. 빌드 커밋은 `a68a358149240597e2bd6529f00850b3ccea9983`이며 깨끗한 작업 트리에서 빌드했습니다. 실행 ID는 `20261006T034314Z-v1.0.10-3ea0bb9`이고, 이번 릴리즈의 로컬 검증은 `2026-10-06T03:43:14.202793+00:00`부터 `2026-10-06T05:30:12.458727+00:00`까지 106분 58초입니다. 검사에는 가상 자료만 사용했습니다.

| 검사 | 최종 후보 결과 |
| --- | --- |
| 단위·저장소 | 150개, 실패·오류·생략 0. 소스의 117개 검사와 Room API 31·35 반복 실행 이름을 대조 |
| Lint·빌드 | Debug·Release 오류 0, 경고 40·41개. 개발·검사용·최적화 서명 Release APK 및 별도 위젯 호스트 빌드 성공 |
| API 35 전체 UI | 기본·글씨 1.5배 각각 70개, 실패·오류·생략·예상 이름 누락 0 |
| API 36 지정 회귀 | 기본·글씨 1.5배 각각 30개. 설정·저장 안내·위젯·키보드·표·입고·초안 재생성 포함, 실패·오류·생략·예상 이름 누락 0 |
| 실제 프로세스 복구 | API 35에서 PID `31346` 종료 후 `31639`에서 미저장 품목 복구·버리기 확인 |
| 업데이트 다운로드 | 실제 DownloadManager·FileProvider·Android 설치 화면, 취소 후 공용 파일 보존·변조 거부 1개 통과 |
| 최종 APK 신규 설치 | API 31에서 62.3kg 기록 저장·프로세스 재실행·재고·설정 진입, DB 무결성 확인 |
| OS 알림 권한 | API 36 새 설치에서 OS 권한 창, 거절 후 재실행 시 재요청 없음, 시스템 알림 설정 열기 확인 |
| 공개 v1.0.9 → 최종 APK | API 35에서 삭제 없이 업데이트. 스키마 8·15개 테이블·55행·584개 기존 필드 값 동일 |
| 백업·권한 | 보호 백업 바이트, 알림·SAF 권한과 기존 DataStore 값 보존. 같은 백업 폴더에 새 파일 생성·기존 파일 보존 |
| 최적화 APK 위젯 | API 31·35·36 각각 별도 호스트·실제 프로세스 종료 5회·혼합 4개 수신자·일정 단독·시각 방송·재부팅 통과. 화면 꺼짐은 35에서 180초, 31·36에서 10초 |
| 실제 Launcher | 기존 기록 위젯 4×2 → 4×1 → 2×2 즉시 재배치, 새로고침·앱 진입, 별도 2×2 추가, 두 위젯의 라이트·다크 12개 단계 표시 확인 |
| 안내·문서·ZIP | 앱 안내 18주제·77단계·참조 PNG 47장(9,124,829바이트) 원본 일치. 배포 ZIP 80개 PNG 원본 일치·CRC 확인 |

마지막 검사 보완은 앱 기능 코드를 바꾸지 않았습니다. 직전 후보와 최종 APK의 ZIP 항목을 비교하여 앱 코드·리소스가 모두 같고 `META-INF/version-control-info.textproto`만 달라진 것을 확인했습니다. 앱·단위 검사 소스는 같아 최종 단위 작업은 Gradle의 `UP-TO-DATE`였으며 보존된 이번 릴리즈의 150개 XML을 사용했습니다. Lint는 변경한 UI 검사 소스의 분석을 수행했고 기존과 같은 경고 40·41개 보고서를 확인했습니다. UI 검사는 최종 검사 소스로 다시 실행했고, APK에 의존하는 신규 설치·업데이트·위젯 검사는 최종 해시 파일로 다시 수행했습니다. 과거 버전의 결과를 대신 사용하지 않았습니다.

Lint 경고는 의존성/AGP 업데이트, 앱 언어 설정, Compose Modifier 인자, 호환 Drawable, API 조건, 위젯 중첩 weight·작은 글씨, KTX 관련 항목입니다. Release에는 단색 런처 아이콘 경고 1개가 추가됩니다. 전체 경고별 내용은 `verified-lint-results-debug.xml`과 `verified-lint-results-release.xml`에 보존했습니다.

검증 중 첫 후보는 변경된 사용 구성 화면의 검사 경로 1개, 둘째는 키보드 전환 중 저장 터치 2개, 셋째는 외부 파일 선택기 복귀 대기 1개가 실패했습니다. 각각 기본 전체 70개 실행 결과를 보존했습니다. 넷째는 입고 초안 재생성 전 입력 확인이 없는 검사 1개가 실패하여 집중 확인을 위해 실행을 중단했습니다(API 35 보고서 39개·실패 2개에는 의도적 실행 중단 오류 1개 포함). 해당 후보의 API 36은 기본 29개 통과 후 큰 글씨 실행을 중단했으며 전체 통과로 처리하지 않았습니다. 다섯째 후보는 기본 70개 중 위젯의 새 TextView를 측정하기 전에 크기를 읽은 검사 1개가 실패했습니다. 레이아웃 준비와 실제 크기 검증을 같은 UI 콜백에서 수행하도록 보완했습니다. 최종 API 31 신규 설치의 첫 화면 탐색 실패는 별도 보존하고 기록 탭의 추가 버튼을 이용해 새 설치부터 다시 확인했습니다. 다섯째 후보의 API 36 기본·큰 글씨 30개는 모두 통과했으나 최종 결과로 대신 사용하지 않았습니다. 경로·키보드 안정화·실제 앱 창 포커스·재생성 전 입력값 및 저장 가능 상태·위젯 측정 완료를 확인한 집중 검사는 모두 통과했습니다. 이후 최종 후보의 기본·큰 글씨 전체 결과를 새로 확인했습니다. 실패·중단 결과나 부분 재실행을 합산하여 전체 통과로 기록하지 않았습니다.

업데이트 가상 자료에는 음수·0·미입력, mL·g·kg, 과거 계산 기준, 취소·비활성 품목, 삭제 기록의 사용 내역, 수정·미저장 초안, 비기본 설정과 과거 축하 설정을 포함했습니다. 품목 6·구성 3·기록 13·사용 14·입고 3·실사 2·감사 4·초안 2·설정 1·진료과 2·치료 구성 1·예약 1·연락처 1·재고 조정 1·입고 요청 1행을 비교했습니다. 재고 화면의 38·100·107·91·20EA가 그대로 유지됐습니다. 기존 전체 백업을 구버전의 실제 파일 선택기로 가져와 준비했으며, 자동 백업의 전체 JSON을 비교할 때 `exportedAt`과 새 선택 필드 `CareTask.time=null`만 제외했습니다. 수동 내보내기 파일을 새로 만들었다고 처리하지 않았습니다. 앱 UID·최초 설치 시각·보호 파일과 SAF `persisted=0x3`, 기존 알림 허용을 보존했으며 DataStore의 업데이트 조회 캐시만 변경 허용했습니다. 최초 후보에서 `notificationRequested` 신규 키 추가를 확인했고 최종 후보에서는 기존 키 값 유지까지 확인했습니다.

| 공개 파일 | 바이트 | SHA-256 |
| --- | ---: | --- |
| `perilog-1.0.10.apk` | 18,563,086 | `fa995dbb135193f9e3ee800c6f4decac61d4b90d6f4c11490f4557b1d5c53945` |
| `perilog-1.0.10-screenshots.zip` | 13,898,509 | `c2fdbb918ba190c33dd46be603e0c313a4c2c91e0fb665f12067c1a4ef3b508d` |
| `perilog-1.0.10.sha256` | 182 | `378f5ddb6ed255adf49799ebdd3a4ba3f3907294686f4c86616a5bc65ab3c944` |

APK 서명은 기존 공개 v1.0.9와 같은 인증서 SHA-256 `79adbeead4f7fbc2a5bf99219ee2fdd909f14eb18817c2b4f6139edbce0937dd`입니다. 공개 구버전 APK SHA-256은 `8adce2433fcddae756957af27bfbb92d0b67769bb55fb16d11934bbf82ad4003`입니다.

환경: macOS 27.0.1 arm64, Temurin JDK 17.0.14+7, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, SDK/Build Tools 36.0.0. 실제 arm64 에뮬레이터는 API 31 `perilog_release_api31 / emulator-5564`, API 35 `perilog_ci_api35 / emulator-5556`와 `perilog_upgrade_audit / emulator-5560`, API 36 `perilog_update_api36 / emulator-5558`입니다. 빌드는 한 번에 하나씩 실행했고 사용자 허용 범위에서 서로 다른 검사 기기·결과 폴더의 ADB 작업만 병렬 진행했습니다.

근거는 `.tools/release-checks/20261006T034314Z-v1.0.10-3ea0bb9/` 아래 `run.json`, `candidate-final6-build.log`, `unit-results-verified/`, `unit-name-audit-verified.json`, `api35-verified/`, `api36/`, `process/`, `update-integration-results/`, `fresh31/`, `fresh36/`, `upgrade-final/`, `widgets31/`, `widgets35/`, `widgets36/`, `results-summary.json`, `artifact-manifest.json`, `candidate5-final-apk-diff.json`입니다. 원격 Android 전체 진단은 미실행·불필요입니다. 저장소 공통 통합 검증 명령과 작업 잠금은 아직 없으며, 이번 실행 폴더의 보조 스크립트와 원본 보고서 대조로 확인했습니다. Galaxy Z 플립7 실물의 삼성 키보드·접기/펼치기·생체 인증·삼성 홈·장시간 절전은 이번 에뮬레이터 검사 범위에 포함되지 않습니다.

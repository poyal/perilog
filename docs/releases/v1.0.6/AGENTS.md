# v1.0.6 최종 검증 결과

[사용자 매뉴얼](README.md) · [GitHub Release](https://github.com/poyal/perilog/releases/tag/v1.0.6) · [공통 개발 규칙](../../development.md) · [배포 절차](../../release.md)

- 공개 태그 커밋: `5035f9e39d8791a52d2bf11904839c6f69163a65`
- 아래 결과는 기존 최종 검증 기록을 정리한 것이며 이번 문서 정리에서 재실행한 결과가 아닙니다. 작업 로그·중간 후보·로컬 설치 파일은 보관하지 않습니다.

## v1.0.6 — 게시와 공개 파일 검증 완료

2026-10-03. **버전 1.0.6 / 코드 7**, 빌드 커밋 `dabff7650da813361e085f2c0e0106a43c2bc6fe`의 깨끗한 작업 트리에서 전체 단위·Lint·Debug/Test/Release 빌드를 실행했습니다. APK를 `dist/perilog-1.0.6.apk`에 고정하고 신규 설치와 업데이트 검사에 같은 바이트를 사용했습니다.

| 검사 | 결과 |
| --- | --- |
| 전체 단위·저장소 | 82개 통과. 소스 62개 방법과 이름 대조, Room API 31·35 포함. 실패·오류·생략·누락 0 |
| Lint | 오류 0. Debug 경고 3, Release 경고 4. 오프라인 실행이며 남은 경고는 SDK 조건·KTX 제안·Release 단색 런처 아이콘 |
| API 35 전체 UI | 기본·1.5배 각각 34개 통과. AppFlowTest 22개·AppointmentFlowTest 10개·UiComponentsTest 2개, 예상/실행 이름 대조 |
| API 36 입력 회귀 | 기본·1.5배 각각 1개 통과. 회전·키보드·입력값과 저장 버튼, 미저장 재고 보존 |
| 실제 프로세스 종료·복구 | process 3900 -> 4112; unsaved product form restored, then discarded. |
| 최종 서명 APK 신규 설치 | API 36, 기록 62.3kg 저장 후 DB 62300g 확인, 재고·설정 진입. 가로·큰 글씨·키보드 화면 확인 |
| 공개 v1.0.5 → v1.0.6 업데이트 | 삭제 없이 덮어 설치. DB 7 유지·무결성 ok, 14개 테이블·34행·323개 기존 필드 전부 일치 |
| 재고·백업·권한 | 37 / 0 / −2 / 22EA 동일. 앱 UID·최초 설치 시각·알림·폴더 권한·DataStore 기존 키·내부 보호 백업 보존. 내보내기 전체 값 일치, 같은 폴더에 새 백업 생성 및 기존 파일 바이트 보존 |
| 촬영·문서 | 촬영 검사 1개 통과. PNG 42장: API 35 개발 빌드 40장, API 36 서명 APK 2장. 매뉴얼·사용 안내·화면 안내를 실제 UI에 맞춤 |

업데이트 자료는 기존 9개 테이블과 연락처·진료과·치료 항목·병원 일정·수량 조정의 5개 테이블 모두에 데이터를 넣었습니다. 기계·추가투석, 음수·0·미입력, mL/g/kg, 과거 계산 기준, 취소 입고·사용·조정, 비활성 품목, 삭제 기록의 사용 내역, 미저장·수정 초안, 비기본 설정, 여러 줄 메모, 예전 시작·종료 시각과 미완성 시각 초안을 포함합니다. 실제 백업 가져오기와 시스템 파일 선택기로 준비했으며 사용자 실제 자료는 사용하지 않았습니다.

연락처 연속 등록·수정·취소·재생성, 좁은 화면/큰 글씨의 달력 7열과 양끝 날짜 선택, 긴 메모 펼치기·접기와 밝은/어두운 테마도 확인했습니다. 사용자가 보고한 연락처 덮어쓰기 자체는 이전 코드의 단순 연속 등록 검사에서 재현되지 않았으나 새 등록의 상태·ID를 분리하고 저장소에서도 덮어쓰기를 거절하도록 보완한 뒤 확장 흐름을 검증했습니다.

Windows 11 x64, JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, Build Tools 36.0.0입니다. `Perilog_Changes / emulator-5560`은 API 35 x86_64, `Perilog_Release36 / emulator-5562`는 API 36 x86_64입니다. 단일 작업 폴더에서 검사를 순차 실행했습니다. 사용자용 에뮬레이터는 초기화하지 않았습니다.

로컬 보조 스크립트로 직접 ADB instrumentation 결과·JUnit XML·HTML·예상 이름을 확인했습니다. 통합 릴리즈 명령이나 자동 게시 전 검증 차단 기능을 구현한 것으로 간주하지 않습니다. 결과 집계 도구가 처음에 `@Test(expected=...)` 3개를 누락한 것은 집계 정규식을 보완해 실제 82개 실행과 대조했습니다. 앱 검사 실패나 APK 변경은 없었습니다.

원격 Android 진단은 미실행·불필요입니다. 업데이트·게시 코드와 최소 SDK는 변경하지 않았으므로 해당 전용 회귀 및 API 31 실제 설치를 추가하지 않았습니다. Galaxy Z 플립7 실물의 접기·펼치기, 삼성 키보드·파일 선택기·생체 인증·장시간 절전은 별도 확인 범위입니다.

APK는 5,635,242바이트, SHA-256 `117bae1059e8de4dc8b5e9fe6ff58593ef67a8c5363a5489acd3899061f68ed6`입니다. 직전 공개 APK와 인증서 SHA-256 `79adbeead4f7fbc2a5bf99219ee2fdd909f14eb18817c2b4f6139edbce0937dd`가 일치합니다. 화면 ZIP은 5,209,383바이트, SHA-256 `f9d69c2d75e9eb73f8116dabf82424d3f5015967c0e54ffe5edcf4612e6d0304`입니다. 체크섬은 LF 줄바꿈의 APK·ZIP 두 항목이며 PNG 42장의 원본·압축 무결성을 확인했습니다. 문서 상대 링크 217개와 README 목차 앵커를 확인했습니다.

### v1.0.6 게시 출처와 공개 확인

| 출처 | 확인 |
| --- | --- |
| buildCommit | `dabff7650da813361e085f2c0e0106a43c2bc6fe` |
| sourceCommit | `1f84539f39684ae721e50826d187e6b63e51028b` |
| build → source | 사용자 문서·릴리즈 노트·검증 기록과 번호 PNG만 49개 경로 변경. 앱·Gradle·검사·촬영 코드 변경 없음 |
| releaseCommit | `5035f9e39d8791a52d2bf11904839c6f69163a65`. 단일 부모는 sourceCommit이며 `.release/`의 해당 버전 세 파일만 추가 |
| 태그 | annotated `v1.0.6`이 releaseCommit을 가리킴. 원격 태그 역참조도 동일 |
| 푸시 | main과 태그를 한 번에 푸시. release 브랜치 중복 푸시 없음 |

[게시 작업 37100433440](https://github.com/poyal/perilog/actions/runs/37100433440)이 성공했습니다. 기존 서명 APK를 재빌드 없이 게시했으며 작업은 15초 걸렸습니다. 실행 목록에서 main·태그 푸시로 무거운 Android 검증이 생성되지 않은 것도 확인했습니다. 게시 전 공개 API와 원격 refs에서 같은 버전·진행 중 게시가 없음을 확인했고, 게시 스크립트의 인증된 GitHub 경로에서 초안/기존 자산을 검사한 뒤 공개했습니다.

2026-10-03T05:38:37.584467+00:00에 [v1.0.6 공개 릴리즈](https://github.com/poyal/perilog/releases/tag/v1.0.6)가 최신 정식 릴리즈이고 초안·사전 공개가 아닌 것을 확인했습니다. 공개 URL에서 세 파일을 다시 내려받아 로컬 원본·Git blob·GitHub 자산 digest와 바이트가 일치함을 확인했습니다. APK 서명·앱 ID·버전 1.0.6/코드 7, ZIP의 원본 PNG 42장과 압축 무결성도 통과했습니다. 태그 푸시 시작부터 공개 재다운로드 확인까지 58.5초 걸렸습니다.

- `perilog-1.0.6-screenshots.zip`: 5,209,383바이트, SHA-256 `f9d69c2d75e9eb73f8116dabf82424d3f5015967c0e54ffe5edcf4612e6d0304`.
- `perilog-1.0.6.apk`: 5,635,242바이트, SHA-256 `117bae1059e8de4dc8b5e9fe6ff58593ef67a8c5363a5489acd3899061f68ed6`.
- `perilog-1.0.6.sha256`: 180바이트, SHA-256 `f5cfca6ed30a39b94e056aee94c04d4aa58a92dc9ee4dacad8b7b1c0cb089130`.

최종 근거는 실행 폴더의 `commit-relationship.json`, `publication-run.json`, `public-release.json`, `public-verification.json`, `public-apk-signature.txt`, `public-apk-manifest.txt`, `public/`에 보관했습니다. 게시 확인 문서만 main의 후속 커밋으로 남기며 이미 공개한 태그와 APK는 유지합니다.

<p align="center">
  <img src="design/icon-concepts/02-daily-journal-bow.png" alt="페리로그 아이콘" width="100">
</p>

# 페리로그

**나의 하루, 나의 투석 기록.**

매일의 복막투석 기록과 사용 물품을 휴대폰에서 관리하는 Android 앱입니다. 몸무게·혈압과 투석 결과를 나누어 적고, 사용한 물품을 재고에 반영하며, 날짜별 기록과 변화를 돌아볼 수 있습니다.

Android 12 이상 · 로그인 없음 · 기록은 기기 내부 저장

**[v1.0.4 APK 다운로드](https://github.com/poyal/perilog/releases/download/v1.0.4/perilog-1.0.4.apk)** · [릴리즈 노트](readme/releases/v1.0.4.md) · [설치·사용 안내](readme/사용-안내.md)

<table>
  <tr><th>오늘의 기록</th><th>투석 결과 입력</th><th>물품과 재고</th></tr>
  <tr>
    <td><a href="readme/screenshots/01-home.png"><img src="readme/screenshots/01-home.png" alt="오늘 남은 기록을 확인하는 홈 화면" width="240"></a></td>
    <td><a href="readme/screenshots/04-after-treatment.png"><img src="readme/screenshots/04-after-treatment.png" alt="초기배액량과 기계 제수량 입력" width="240"></a></td>
    <td><a href="readme/screenshots/12-inventory.png"><img src="readme/screenshots/12-inventory.png" alt="품목별 색상과 남은 EA 수량" width="240"></a></td>
  </tr>
</table>

## 할 수 있는 일

| 기능 | 내용 |
| --- | --- |
| 매일 기록 | 몸무게·혈압, 사용 구성, 투석 결과를 단계별로 저장하고 미작성 항목 확인 |
| 추가투석 | 하루 여러 건 기록, 물품 사용만 저장 가능, 배액량·무게는 입력한 단위로 보관 |
| 기록과 통계 | 리스트·캘린더·기간별 표, 혈압·몸무게·투석 결과의 라인차트 |
| 물품과 재고 | 품목별 색상, 자주 쓰는 구성, 일괄 입고, 사용기한과 EA 수량 관리 |
| 백업 | 전체 JSON 내보내기·가져오기, 선택한 폴더에 주기적 백업 |
| 표시와 안내 | 밝게·어둡게·시스템 테마, 큰 글씨와 가로 입력, 미작성 알림, 선택형 앱 잠금 |

시작 전 항목만 먼저 저장하고 투석이 끝난 뒤 결과를 이어 적을 수 있습니다. 초안은 재고를 차감하지 않으며, 저장한 기록을 수정하거나 다시 저장해도 물품 사용량을 중복 차감하지 않습니다.

## v1.0.4에서 달라진 점

- 다크모드의 본문·보조 설명·입력칸·카드 구분을 더 선명하게 다듬었습니다.
- 설정의 설명과 토글을 좌우로 정렬했습니다. 항목 전체를 눌러 전환할 수 있습니다.
- **설정 → 페리로그 정보**에서 버전·제작자·문의 링크와 업데이트를 확인할 수 있습니다.
- 새 버전을 알리고, 앱에서 APK 다운로드와 체크섬 검증 후 Android 설치 화면으로 연결합니다.

<table>
  <tr><th>다크모드</th><th>페리로그 정보</th><th>정리된 설정</th></tr>
  <tr>
    <td><a href="readme/screenshots/20-dark-mode.png"><img src="readme/screenshots/20-dark-mode.png" alt="남색 배경과 밝은 글씨의 홈 화면" width="240"></a></td>
    <td><a href="readme/screenshots/26-about-dark.png"><img src="readme/screenshots/26-about-dark.png" alt="버전·제작자·문의 링크가 있는 정보 화면" width="240"></a></td>
    <td><a href="readme/screenshots/30-settings-toggles.png"><img src="readme/screenshots/30-settings-toggles.png" alt="설명 왼쪽·토글 오른쪽으로 정렬한 설정 화면" width="240"></a></td>
  </tr>
</table>

[전체 화면 안내](readme/화면-안내.md) · [스크린샷 원본 ZIP](https://github.com/poyal/perilog/releases/download/v1.0.4/perilog-1.0.4-screenshots.zip)

화면은 v1.0.4의 API 35·36 에뮬레이터에서 가상 데이터로 촬영했습니다. 24·30번은 배포하는 서명 APK, 나머지는 같은 소스의 개발 빌드입니다. 개발 빌드의 업데이트 상태는 검사 응답을 사용한 예시입니다.

## 설치와 업데이트

1. 휴대폰에서 [APK](https://github.com/poyal/perilog/releases/download/v1.0.4/perilog-1.0.4.apk)를 내려받아 엽니다.
2. Android가 요청하면 파일을 연 앱의 **이 출처 허용**을 켜고 설치합니다.
3. 페리로그를 실행합니다. 계정이나 회원 가입은 필요하지 않습니다.

**업데이트는 앱을 삭제하지 않고 기존 앱 위에 설치하세요.** v1.0.3 → v1.0.4의 기록·재고·설정·백업 권한 보존을 확인했습니다.

v1.0.4부터는 **페리로그 정보 → 업데이트 확인 → APK 다운로드 → 업데이트 설치**로 진행할 수 있습니다. 자동 조회는 하루 한 번이며, 확인된 새 버전은 앱을 완전히 종료한 뒤 새로 시작할 때 안내합니다. 설치를 취소하거나 보안 설정으로 차단돼도 APK는 Download 폴더에 남습니다.

## 처음 사용하는 순서

1. **재고 → 품목 관리 · 색상**에서 사용하는 물품을 등록합니다.
2. **입고 등록**으로 보유 수량을 넣고, **사용 구성 관리**에서 자주 쓰는 조합을 저장합니다.
3. **설정**에서 이전 최종 주입 설정값을 확인하고 백업 폴더를 연결합니다.
4. 홈에서 오늘 기록을 시작합니다. 이전 수치는 참고값이므로 오늘 측정값으로 확인한 뒤 저장합니다.

날짜 선택, 총 제수량 계산 방식, 사용 취소와 재고 조정은 [사용 안내](readme/사용-안내.md)에 정리했습니다.

## 데이터와 백업

기록·재고·설정은 기기 내부에 보관하며, GitHub에는 업데이트 조회·다운로드 요청만 보냅니다. 기록과 백업을 업로드하지 않습니다.

**앱 삭제·기기 변경 전에는 설정에서 전체 데이터를 내보내 주세요.** 백업 가져오기는 현재 데이터를 전체 교체하며, 복원 직전 데이터는 앱 내부 보호 백업으로 남깁니다. JSON 백업은 암호화하지 않으며 앱 잠금도 외부 백업 파일에는 적용되지 않습니다.

## 문서와 문의

- [설치·사용 안내](readme/사용-안내.md) · [전체 화면 안내](readme/화면-안내.md)
- [개발·빌드 안내](readme/개발-안내.md) · [E2E 실행 방법](readme/E2E-테스트.md)
- [로컬 검증·릴리즈 운영 가이드](readme/로컬-검증-후-배포-운영-가이드.md) · [검증 기록](readme/검증-기록.md)
- [계획·결정 이력](readme/plan.md) · [이전 릴리즈](https://github.com/poyal/perilog/releases)
- [버그 신고·기능 제안](https://github.com/poyal/perilog/issues) · [이메일 문의](mailto:poyal.work@gmail.com)

릴리즈는 로컬에서 검증한 서명 APK를 그대로 게시합니다. GitHub Actions는 파일 게시를 담당하며, 무거운 Android 검사는 수동 진단용으로만 실행합니다.

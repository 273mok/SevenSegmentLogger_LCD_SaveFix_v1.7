# SevenSegmentLogger v1.6 — CSV 기록 타이머 오류 수정

## 수정 내용

- v1.5의 예약 기록 코드에서 `SystemClock.elapsedRealtime()`로 만든 시각을 `Handler.postAtTime()`(uptimeMillis 기준)에 그대로 넘겼던 문제를 수정했습니다. 갤럭시가 부팅 이후 절전한 시간만큼 기록이 지연될 수 있었습니다.
- 이제 `Handler.postDelayed()`에 **현재 elapsedRealtime 기준으로 계산한 지연시간**을 전달하여 0.5초마다 기록을 시도합니다.
- 측정 중 `기록: N개` 표시를 확인하세요. 측정 시작 후 5초라면 약 10개로 증가해야 합니다.
- 인식한 값이 바뀌지 않아도 매 0.5초마다 한 행을 기록합니다. 불확실한 인식은 공란과 상태 코드로 기록합니다.

## 실제 휴대폰 CSV 저장 방법

1. **측정 시작**을 누르고 `기록: N개`가 증가하는지 확인합니다.
2. **측정 중지**를 누릅니다.
3. **CSV 저장/내보내기**를 눌러 Android 파일 저장 창에서 `다운로드` 등 원하는 폴더를 선택합니다.
4. 파일명을 확인한 뒤 **저장**을 누릅니다.
5. 갤럭시 `내 파일 → 다운로드`에서 CSV를 확인합니다.

자동 기록은 먼저 **앱 전용 내부 폴더**에 저장됩니다. 그래서 `내 파일`의 다운로드 폴더에는 자동으로 보이지 않으며, 반드시 CSV 저장/내보내기를 해야 접근 가능한 곳에 복사됩니다.

## GitHub 업데이트

기존 저장소의 아래 두 파일만 교체하세요.
- `app/src/main/java/com/example/sevensegmentlogger/MainActivity.java`
- `app/build.gradle`

이후 GitHub Actions → Build Galaxy APK → Artifacts에서 APK를 받으면 됩니다. 기존 `.github/workflows/build-apk.yml`이 정상 작동한다면 수정할 필요 없습니다.

## CSV 형식

`timestamp_utc,elapsed_seconds,value,status`

카메라가 제시간에 갱신되지 않으면 마지막 판독값을 최대 900ms까지 사용하거나 `NO_FRESH_FRAME`을 기록할 수 있습니다. 0.5초 주기의 실제 카메라 프레임 취득과 기록 시각은 완전히 동일하지 않을 수 있습니다. 백그라운드/화면 꺼짐 중 기록은 보장하지 않습니다.

실제 APK 컴파일 및 실기기 실행은 아직 검증되지 않았습니다.

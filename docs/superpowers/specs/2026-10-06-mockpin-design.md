# MockPin MVP

Android 개인용 공식 Mock Location 도구. Kotlin/Compose, package `com.gnojes.mockpin`, minSdk 31, compile/targetSdk 36. 루팅·탐지 우회·백그라운드 위치 권한·자동 부팅 시작·서버 없음.

## 구조
- `SavedLocation` / 좌표 검증 / 최근 기록 정책: Android와 독립적인 모델.
- `LocationStore`: 단일 DataStore에 즐겨찾기와 최근 10개 저장. 약 10m 이내 위치는 최근 목록에서 합침.
- `MockLocationService`: location FGS, FLP mock mode와 1초 반복 업데이트 소유. 프로세스 내 StateFlow가 실제 서비스 상태의 유일한 출처. STARTING/ACTIVE/STOPPING/INACTIVE 및 오류 표시. START_NOT_STICKY.
- `MockSession`: enable → 반복 send → finally disable을 직렬 실행. STOP/예외/Activity 종료에 안전. OS 강제 종료는 finally를 보장하지 못함.
- `PlacesRepository`: New API 초기화, 최대 5개 자동완성, 350ms debounce, 최소 2자, 세션 토큰 공유, 상세는 ID/DISPLAY_NAME/FORMATTED_ADDRESS/LOCATION만 요청.
- `MockPinViewModel`: 선택 위치/검색/저장 상태, Activity는 가시 상태에서만 FGS 시작. 실행 중 새 위치는 별도 적용 버튼으로 전환.
- Compose: 검색 상단, 지도 중앙, 제어 패널 하단, 즐겨찾기/최근/좌표 다이얼로그. 선택 marker와 현재 실행 marker 구분.

## 권한 및 보안
FINE/COARSE 위치 권한 함께 요청, 알림 권한은 Android 13 이상에서 요청. START 전 위치 서비스, AppOps 공개 mock location 상태, Google Play services 확인. 실제 setMockMode 실패도 설정 오류로 처리. 개발자 옵션/위치 설정 공개 Intent 사용.
`secrets.properties`를 Gradle Properties로 읽어 Manifest placeholder 및 BuildConfig에 주입. 예제는 DEFAULT_API_KEY. 실제 파일 gitignore. 클라이언트 APK 안의 키는 추출 가능하므로 패키지/SHA-1/API 제한 안내.

## 검증
좌표 범위/비정상 숫자, 최근 중복/10개 제한, mock 세션 enable/send/disable 순서와 cancellation/실패 정리 단위 테스트. 최종 testDebugUnitTest 및 assembleDebug. 실제 지도 인증 오류는 Maps SDK가 앱에 콜백을 제공하지 않아 설정 안내 및 로딩 지연 안내 제공. 실기기 공급과 실제 위치 복귀는 Galaxy Android 16에서 확인 필요.

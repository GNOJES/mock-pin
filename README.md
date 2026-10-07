# MockPin

Android 개인용 모의 위치 도구. Kotlin + Jetpack Compose, `com.gnojes.mockpin`, minSdk 31, compileSdk/targetSdk 36. Android 개발자 옵션과 Google Play services가 제공하는 공식 Mock Location API만 사용합니다.

## 구현된 기능

- Google Maps 지도 탭, Places API (New) 장소/주소 검색, 직접 좌표 입력으로 위치 선택.
- 지도 우측 상단의 **내 위치로 이동**은 카메라만 이동합니다. Mock 실행 중에는 적용 중인 가상 위치로, 중지 상태에서는 휴대폰의 현재 위치로 이동하며 START 대상은 유지됩니다. 현재 위치 권한은 버튼을 누를 때 요청하고, 위치 수신 실패 시 다시 시도 안내를 표시합니다.
- 앱 시작 시 키보드는 숨겨져 있으며 검색창을 직접 누르면 열립니다.
- 자동완성 최소 2자, 350ms debounce, 최대 5개 결과, 같은 입력 중복 요청 억제, 검색 세션별 토큰. 상세 조회는 ID·표시 이름·주소·좌표만 요청.
- START → location Foreground Service → `setMockMode(true)` → 약 1초 간격 `setMockLocation()`.
- 매 업데이트 새 timestamp와 elapsedRealtimeNanos, accuracy 5m, speed/bearing 0. 실행 중 화면을 닫거나 다른 앱으로 이동해도 서비스가 유지됩니다.
- STOP → 공급 중단 → `setMockMode(false)` 완료 대기 → 알림 제거 및 서비스 종료. 알림에서도 STOP 가능.
- 선택 위치는 파란 marker, 적용 중인 위치는 빨간 marker. 실행 중 다른 곳을 선택하면 **선택 위치 적용**을 눌러 변경합니다.
- 즐겨찾기 이름·좌표·주소 저장 및 삭제. 최근 실제 적용 위치 10개, 약 10m 이내 중복 제거. DataStore 로컬 저장, 백업/클라우드 전송 비활성화.
- 권한/개발자 옵션/위치 서비스/FGS/Places 네트워크·quota 오류 안내와 설정 이동.
- 서비스 시작·실행·정리 상태를 별도로 표시. 화면 회전은 공급 상태를 변경하지 않습니다. 강제 종료 후 정리를 확인할 수 없으면 **Mock Mode 정리** 제공, 자동 재시작 없음.

## Demo API Key 설정

프로젝트 루트의 **`secrets.properties`**에 본인의 Demo Key를 붙여 넣습니다.

```properties
MAPS_API_KEY=DEFAULT_API_KEY
```

위 placeholder 값을 본인 키로 바꾸세요. 실제 파일은 `.gitignore`에 포함됩니다. `secrets.defaults.properties`는 Git에 넣어도 되는 placeholder 파일입니다. 키는 Gradle에서 읽어 Manifest placeholder와 BuildConfig에 주입하며 Kotlin 소스와 Manifest 원본에 실제 값을 적지 않습니다. 변경 후 Gradle Sync와 재빌드/재설치가 필요합니다.

`secrets.properties`가 존재하면 그 파일이 우선됩니다. `secrets.defaults.properties`에 실제 키를 넣으면 기존 secrets 파일의 placeholder가 계속 사용될 수 있습니다. 기본 파일에는 실제 키를 넣지 마세요.

Google의 **Maps Demo Key**는 제한된 API만 지원하며, 현재 공식 지원 목록에 **Maps SDK for Android**는 포함되지 않습니다. 이 유형의 키라면 Android 지도를 사용하려면 프로젝트에 결제 계정을 연결하고 Maps SDK for Android를 활성화하거나 해당 SDK를 지원하는 일반 API 키를 발급해야 합니다. [Demo Key 지원 범위](https://developers.google.com/maps/demo-key)를 확인하세요.

Google Cloud 프로젝트에서 다음을 확인하세요.

1. **Maps SDK for Android**와 **Places API (New)** 활성화. Legacy Places API로 초기화하지 않습니다.
2. Demo Key의 유효기간, quota 및 해당 프로젝트의 결제 설정 확인.
3. 키의 애플리케이션 제한을 Android 앱으로 설정: 패키지 `com.gnojes.mockpin` + 실제 서명 SHA-1. Debug SHA-1은 `./gradlew signingReport`로 확인할 수 있습니다. Release 서명은 별도 SHA-1을 등록해야 합니다.
4. API 제한은 사용하는 Maps/Places API만 허용.

클라이언트 APK에는 실행에 필요한 키가 포함됩니다. 로컬 secrets 파일은 소스 유출을 방지하며, APK에서 추출되는 키의 사용은 Google Cloud의 패키지/SHA-1/API 제한으로 제어합니다. 빌드 산출물과 Gradle 캐시는 Git에 넣지 마세요.

키가 placeholder인 빌드에서도 좌표 입력·즐겨찾기·모의 위치 기능을 사용할 수 있습니다. 지도와 Places 검색은 유효한 키가 필요합니다.

## Galaxy Android 16 최초 설정

1. APK 설치 후 휴대폰의 **위치 서비스**를 켭니다.
2. **설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드번호**를 7번 눌러 개발자 옵션을 켭니다. 필요한 경우 기기 잠금 인증을 진행합니다.
3. **설정 → 개발자 옵션 → 모의 위치 앱 선택 → MockPin**을 선택합니다. 이름과 메뉴 위치는 One UI 버전에 따라 다를 수 있습니다. 앱의 오류 안내에서도 개발자 옵션을 열 수 있습니다.
4. MockPin에서 START를 누르면 **앱 사용 중 위치 권한**과 **정확한 위치**를 허용합니다. 백그라운드 위치 권한은 요청하지 않습니다.
5. 알림 권한을 허용하면 지속 알림과 알림의 STOP 액션을 사용할 수 있습니다. 거부해도 서비스 실행은 가능하며 MockPin 화면에서 STOP할 수 있습니다.

USB 디버깅, ADB 상시 연결, root, Shizuku는 필요하지 않습니다.

## 실기기 테스트 순서

**MockPin 실행 → 장소 검색 → 위치 선택 → START → 다른 지도 앱 실행 → 위치 변경 확인 → MockPin으로 돌아와 STOP → 실제 위치 복귀 확인**

1. 서울역 등 장소를 검색하고 결과를 선택합니다. 지도 카메라·파란 marker·좌표·주소가 함께 변경되는지 확인합니다. 해외 장소도 검색해 봅니다.
2. START 후 `Mock Location ACTIVE`와 현재 적용 좌표, 지속 알림을 확인합니다.
3. 다른 지도 앱에서 현재 위치 버튼을 눌러 선택 위치로 이동하는지 확인합니다. 각 앱의 마지막 위치 캐시 때문에 새 위치 갱신이 필요할 수 있습니다.
4. MockPin으로 돌아와 다른 위치를 지도 탭/좌표/즐겨찾기로 선택합니다. 적용 중인 좌표는 그대로이며 **선택 위치 적용**을 누른 뒤 새 위치로 변경되는지 확인합니다.
5. 실행 중 화면 회전, 홈 이동, 최근 앱 목록에서 Activity 닫기를 시도합니다. 서비스·알림이 유지되는지 확인합니다. 알림을 누르면 MockPin으로 돌아와야 합니다.
6. 앱의 STOP 또는 알림의 STOP을 누릅니다. `정리 중…` 후 `INACTIVE`가 되고 알림이 사라지는지 확인합니다. STOP을 연속으로 눌러도 정리가 겹치지 않아야 합니다.
7. 다른 지도 앱에서 현재 위치를 다시 요청하여 실제 위치로 복귀하는지 확인합니다. 실제 GPS 수신과 다른 앱의 캐시 갱신 때문에 시간이 걸릴 수 있습니다.
8. 잘못된 좌표 (`91`, `181`, `NaN`, 빈 입력)를 넣으면 적용할 수 없는지 확인합니다. 유효 범위 끝값 및 음수 좌표도 확인합니다.
9. 즐겨찾기 저장/삭제, 앱 재실행 후 복원, 같은 위치 START 반복 후 최근 중복 제거와 10개 제한을 확인합니다.
10. 모의 위치 앱 미선택, 위치 권한 거부, 위치 서비스 끔, 검색 중 네트워크 끊김에서 앱이 죽지 않고 안내를 표시하는지 확인합니다.

## 파일 구조

| 파일 | 역할 |
| --- | --- |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | SDK·stable 의존성·키 주입 |
| `MainActivity.kt` | 사용자 START, 런타임 권한, 서비스 명령, 공개 설정 Intent |
| `MockPinViewModel.kt` | 선택 위치와 검색 상태, SavedStateHandle, 저장 목록 관찰 |
| `location/MockLocationService.kt` | 실제 상태의 출처, location FGS, 알림, 명령 직렬화·정리 |
| `location/MockSession.kt`, `FusedMockDriver.kt` | enable/send/disable 수명주기, FLP 호출, timestamp |
| `location/SessionOwnership.kt`, `MockStatus.kt`, `MockSetup.kt` | 취소 중 작업 점유, 상태·설정 검사·오류 |
| `search/PlacesRepository.kt` | Places New 초기화·세션·최소 필드 상세 조회 |
| `data/LocationStore.kt`, `SavedLocation.kt`, `RecentRecorder.kt` | DataStore, 좌표 검증·거리 기반 중복 제거·저장 재시도 |
| `ui/MockPinScreen.kt`, `LocationMap.kt`, `LocationDialogs.kt` | 지도·상태·버튼·검색·좌표·저장 UI |
| `app/src/test/...` | 좌표·최근 정책·mock 수명주기 및 경합 회귀 테스트 |

## 빌드 및 검증

Android Studio에서 Gradle JDK를 호환 JDK로 설정하고 SDK 36을 설치합니다. 기존 AGP 9.4.1 / Gradle 9.6.0 / Kotlin Compose plugin 2.2.10 구조를 유지했습니다.

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lintDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

SDK 36에 맞는 stable 버전 조합을 사용합니다. Maps Compose 9.0과 최신 AndroidX는 compileSdk 37을 요구해 Maps Compose 6.12.2 등을 사용했습니다. Places 6.0.2는 programmatic `fetchPlace`를 제거했으므로 최소 필드 Place Details를 지원하는 stable 5.3.0을 사용하며 **API는 New로 초기화합니다**. 자세한 버전은 Gradle 설정을 참고하세요.

## 제한

- Galaxy 실기기의 공급·실제 위치 복귀는 사용자 기기에서 위 순서로 확인해야 합니다. 단위 테스트는 Google 서버나 기기 GPS를 호출하지 않습니다.
- Maps SDK는 인증/지도 타일 오류를 앱에 구조화된 콜백으로 전달하지 않습니다. 빈 지도나 로딩 지연 시 지도/API 도움말에서 키·API 제한·네트워크·quota 안내를 제공합니다. Places 오류는 직접 표시합니다.
- Android의 강제 종료/프로세스 kill/재부팅은 `finally` 실행을 보장하지 않습니다. 정리 완료 전에는 STOPPING을 유지하고, 이전 실행 정리 여부가 불명확하면 다음 앱 실행에 정리 버튼을 표시합니다. 재부팅 후 자동 공급은 없습니다.
- 화면이 꺼져도 1초 업데이트를 이어가기 위해 실행 중 partial wake lock을 사용하며 STOP·서비스 종료 시 해제합니다. 사용 시간에 따라 배터리를 소모합니다. Samsung 절전 정책이나 Android 작업 관리자에서 서비스를 중지하면 공급도 중지됩니다.
- 다른 앱이 mock 위치를 거부하거나 캐시를 유지할 수 있습니다. 탐지 우회는 구현하지 않습니다.
- 경로·joystick·속도/altitude/bearing UI·로그인·서버·클라우드 동기화·Play Store 배포 대응은 이번 버전 범위에 포함하지 않습니다.

공식 참고: [FLP mock mode](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient), [location FGS](https://developer.android.com/develop/background-work/services/fgs/service-types#location), [Places Autocomplete New](https://developers.google.com/maps/documentation/places/android-sdk/place-autocomplete), [Places release notes](https://developers.google.com/maps/documentation/places/android-sdk/release-notes), [Maps Compose releases](https://github.com/googlemaps/android-maps-compose/releases).

## 릴리즈 빌드

`./gradlew testDebugUnitTest assembleRelease lintRelease`로 빌드합니다. APK는 `app/build/outputs/apk/release/app-release.apk`에 생성되며, 요청한 대로 로컬 Android debug keystore로 서명합니다. 현재 `versionName`은 `0.1.1`, `versionCode`는 `2`입니다.

API 키는 Git에 포함하지 않습니다. 빌드하려면 로컬 `secrets.properties`에 본인의 키를 설정해야 합니다. 다른 컴퓨터의 debug keystore를 사용하면 SHA-1도 달라지므로 Google Cloud Android 앱 제한에 해당 서명 지문을 등록해야 합니다.

### 앱 아이콘

파란 배경에 흰 위치 핀과 청록색 교환 화살표를 조합한 1번 시안을 Android vector/adaptive icon으로 적용했습니다. 런처 마스크에 대응하는 여백과 테마 아이콘용 monochrome 리소스를 포함합니다. 원본 시안은 `docs/design/mockpin-icon-concept-1.png`에 보관합니다.

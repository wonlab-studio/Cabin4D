# Cabin 4D — 차 안을 4D 영화관으로

정차·충전 대기 중 Pleos Connect 대화면으로 영화를 볼 때, 장면에 맞춰 **공조 바람(풍량·송풍 방향)** 과
**무드등·실내등·독서등** 을 제어해 몰입감을 높이는 차량용 앱입니다.
2026 현대자동차그룹 차량용 앱 개발 공모전 출품작입니다.

- **APK**: [`apk/Cabin4D_0.3.0-debug.apk`](apk/Cabin4D_0.3.0-debug.apk) (Pleos Connect Emulator v2.1.0에서 실행 확인)
- **시연 영상**: [`demo/Cabin4D_demo.mp4`](demo/Cabin4D_demo.mp4) (1분 10초. 앱 화면은 에뮬레이터 실행 화면, 효과 장면은 앱이 차량에 보내는 명령을 그림으로 옮긴 것)
- **설치**: `adb install apk/Cabin4D_0.3.0-debug.apk` → 앱 목록에서 `Cabin 4D` 실행
- **테스트 환경**: Pleos Connect Emulator v2.1.0(AAOS API 34), Vehicle SDK 2.3.2, 인터넷 연결 필요(영상 스트리밍)

## 상영 목록 (CC 라이선스 공개 영화)

| 영화 | 길이 | 4D 하이라이트 | 영상 출처(스트리밍) | 라이선스·저작자 표시 |
| --- | --- | --- | --- | --- |
| Sintel (2010) | 14:48 | 눈보라 습격, 어른 용의 습격, 용의 불길, 무너지는 동굴 | download.blender.org (1080p MKV) | CC BY 3.0 · © copyright Blender Foundation \| durian.blender.org |
| Tears of Steel (2012) | 12:14 | 하늘을 나는 거대 기계, 홀로그램 섬광, 로봇의 습격, 최후의 전투 | download.blender.org (720p MOV) | CC BY 3.0 · (CC) Blender Foundation \| mango.blender.org |
| Big Buck Bunny (2008) | 9:56 | 과일 공격, 날다람쥐 활공, 쿠키 영상 연날리기 | archive.org (1080p MP4) | CC BY 3.0 · (c) copyright 2008, Blender Foundation \| www.bigbuckbunny.org |
| 4D 효과 테스트 | 1:00 | 영상 없이 효과만 차례로 | 없음(시뮬레이션) | — |

- 영상은 **인터넷 스트리밍** 이라 에뮬레이터·차량이 인터넷에 연결돼 있어야 합니다. APK를 가볍게 유지하려고 영상 파일을 넣지 않았습니다.
- CC BY는 저작자 표시가 조건입니다. 목록 카드와 재생 화면 위쪽에 표시 문구를 항상 보여 줍니다(트랙 JSON의 `attribution`·`license`).
- 공모전 심사 환경에 인터넷이 없을 수 있다면, 영상 일부를 잘라 `assets/videos/`에 넣고 트랙의 `media`를 `asset:///videos/…`로 바꾸면 됩니다(자른 사실도 함께 표시).

## 화면 구성

- **홈 화면**: 위에는 대표 영화(스틸컷, 소개, `4D로 재생`·`YouTube로 보기` 버튼)를 크게 보여 주고 몇 초마다 다음 영화로 넘어갑니다. 아래에는 가로로 넘기는 선반 두 줄이 있습니다. `4D로 보는 영화`는 앱이 직접 재생하는 영화, `YouTube`는 공식 YouTube 플레이어로 재생하는 영상입니다. 카드 아래 색 띠는 영화 전체의 무드등 색 흐름입니다.
- **글자·버튼 크기**: Pleos Connect 화면은 크고 밀도가 낮아서 휴대폰 기준 크기로는 너무 작게 나옵니다. `ui/CarScale.kt`가 앱 창 가로를 1100dp로 보고 화면 전체를 키웁니다.
- **YouTube 재생 화면**: YouTube IFrame 플레이어를 WebView에 띄웁니다. YouTube 규정상 플레이어 위에 다른 요소를 덮을 수 없어서 제목 줄(뒤로 가기, 4D 효과 스위치)을 플레이어 위쪽 바깥에 두고, 재생·탐색은 YouTube 플레이어의 버튼을 씁니다. 4D 효과는 플레이어 API가 알려 주는 재생 위치에 맞춥니다(`clock/YouTubeClock.kt`). 영상 목록은 `catalog/YouTubeCatalog.kt`에 있고, `trackId`가 있는 영상만 4D 효과가 나옵니다.
- **재생 화면**: 영상이 앱 창 전체를 쓰고, 조작 버튼은 YouTube 앱처럼 영상 위에 겹쳐 나옵니다. 화면을 누르면 가운데에 `-10초 · 재생/일시정지 · +10초`, 아래에 재생 시간과 탐색 막대가 나타났다가 3.5초 뒤 사라집니다. 화면 왼쪽·오른쪽을 두 번 누르면 10초씩 이동합니다.
- **자막·배속**: 재생 화면 오른쪽 아래의 `자막`, `배속` 버튼으로 고릅니다. 배속은 0.5x~2x이고, 4D 효과는 재생 위치를 따라가므로 속도를 바꿔도 장면과 맞습니다. 자막은 트랙 JSON의 `subtitles`(`lang`, `label`, `uri`)에 적은 SubRip 파일을 씁니다. Sintel은 Blender가 배포한 한국어·영어 자막, Tears of Steel은 공식 영어 자막과 이를 옮긴 한국어 자막을 `assets/subs/`에 넣었습니다(Big Buck Bunny는 대사가 없음). YouTube 재생 화면에서는 YouTube 플레이어의 설정(톱니바퀴)을 그대로 씁니다.
- **4D 효과 스위치**: 오른쪽 위 스위치 하나로 켜고 끕니다. 끄거나, 일시정지하거나, 영상이 끝나거나, 화면을 나가면 공조와 조명은 재생 전 설정으로 돌아갑니다. 강도 조절·명장면 바로가기·효과 로그 패널과 '정차 중' 표시는 화면을 단순하게 하려고 뺐습니다(주행 규제로 막혔을 때만 알림이 뜹니다).
- **효과 테스트 화면**: 홈 선반의 `4D 효과 테스트` 카드. 영상 없이 효과만 내면서 지금 나가는 효과와, 차량이 명령을 받아들였는지 다시 읽어 본 결과를 보여 줍니다.
- 재생 중에는 상태 표시줄·내비게이션 바를 숨기도록 요청합니다(플랫폼이 허용하는 범위에서. Pleos의 주행 정보·GNB 같은 시스템 영역은 앱이 가릴 수 없음).
- **왼쪽 차량 정보 영역까지 넓힐 수 없는 이유**: Pleos 문서에 따르면 Driving view(속도·경고등·차량 상태)는 운전자 쪽 고정 영역이며 앱이 숨기거나 끌 수 없습니다. 앱은 매니페스트의 `screenOrientation="landscape"`로 앱 영역 두 칸을 모두 쓰는 것이 최대입니다(지금 그렇게 설정돼 있음).

## 동작 방식

```
[이펙트 트랙 JSON] ──┐
                     ├─> EffectScheduler ──> CabinActuator ──> Vehicle SDK (HVAC · Light)
[재생 시계]  ─────────┘     (선행 보상·간격 제한·강도)        (원래 설정 저장/복원)
      ▲
[DrivingGate: CarUxRestrictions] ── 주행 규제 시 재생·효과 즉시 정지, 원래 설정 복원
```

화면 영상을 실시간으로 분석하지 않습니다. 4D 영화관처럼 영화마다 "몇 초에 어떤 효과"를 적은
**이펙트 트랙** 을 만들어 두고, 재생 위치에 맞춰 효과를 냅니다.
넷플릭스 같은 외부 앱은 DRM과 화면 캡처 차단 때문에 영상을 읽을 수 없으므로, 앱 안의 플레이어로 재생합니다.

| 폴더 | 내용 |
| --- | --- |
| `effect/` | 트랙 모델·파서, **EffectScheduler**(안드로이드 의존성 없는 순수 Kotlin, 단위 테스트 포함) |
| `vehicle/` | `PleosActuator`(Vehicle SDK 제어), `LoggingActuator`(로그만), `PleosReflect`(SDK 호출 도우미) |
| `safety/` | `DrivingGate`: 플랫폼 주행 규제 상태 수신 |
| `clock/` | 재생 시계: ExoPlayer, 영상 없는 시뮬레이션 |
| `ui/` | Compose 화면(왼쪽 영상, 오른쪽 효과 패널·로그) |
| `assets/tracks/` | 영화별 트랙(`sintel.json` 등)과 효과 테스트 `demo_60s.json`. 폴더의 JSON을 모두 목록에 보여 줌 |
| `tools/track-maker/` | 트랙 생성 도구(영상 분석 → 무드등·섬광 자동 생성 + 장면 정의로 바람). 아래 "트랙 만들기" 참고 |

## 시작하기

1. **Pleos Playground** 가입 → Workspace > My Project에서 프로젝트 생성
2. Android Studio에 **Pleos Connect Emulator v2.1.0** 설치, AVD는 Automotive · RAM 4GB · **Cold boot**
   (GPU 최소 RTX 3060 노트북 6GB급) — 공식 [설치 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
3. 이 폴더를 Android Studio로 열고 Gradle Sync → 에뮬레이터에서 Run
4. 홈 화면에서 영화를 고르면 스트리밍으로 재생됩니다. `4D 효과 테스트`는 영상 없이 60초 동안 효과만 내 보는 **시뮬레이션 모드** 입니다.
5. 차량 쪽 반응은 **Virtual Console** 로 확인합니다.
   ```
   adb forward tcp:8088 tcp:8088
   # 브라우저에서 http://127.0.0.1:8088/ → Climate / Convenience 도메인 확인
   ```
6. 주행 게이트 테스트: Virtual Console에서 기어를 D로 바꾸거나 속도를 올려 보세요. 화면이 "주행 중에는 재생할 수 없습니다"로 바뀌고 효과가 원래 설정으로 돌아가야 정상입니다(시연 영상의 핵심 장면).

단위 테스트: `./gradlew :app:testDebugUnitTest`

### SDK 시그니처 (2.3.2 소스로 확인함)

| 기능 | 시그니처 |
| --- | --- |
| 풍량 | `setHvacFanSpeed(area: SeatArea, value: Int, …)`, 범위·영역은 `checkHvacFanSpeedCapabilities` → `Map<SeatArea, AreaBounds<Int>>` |
| 송풍 방향 | `setHvacFanDirection(area: SeatArea, value: FanDirectionType, …)` — `FACE`, `FLOOR`, `FACE_AND_FLOOR`, `DEFROST` 등 8종 |
| AUTO | `setHvacAuto(area: SeatArea, value: Boolean, …)`, `getHvacAutoState(area)` |
| 무드등 | `setAmbientLightSwitch(area, LightSwitch)`, `setAmbientLightBrightness(area, Int)`, `setAmbientLightBrightnessColor(area, Int /* ARGB, 알파 0xFF */)`, 영역·밝기 범위는 `checkAmbientLightBrightnessCapabilities` |
| 무드등 자동색 | `setAmbientColorAutoByDriveModeEnabled(Boolean)` — 효과 중에는 끄고 끝나면 복원 |

무드등 API는 **좌석 영역(SeatArea)이 필수** 입니다. 앱은 capability로 받은 영역마다 색·밝기·스위치를 보냅니다.

### 첫 실행 후 확인: Logcat 시그니처

공개 문서에서 `setHvacFanSpeed`, `setAmbientLightBrightnessColor` 등의 정확한 파라미터를 확인할 수 없어서,
`PleosReflect`가 공식 샘플 앱과 같은 방식(리플렉션)으로 파라미터를 맞춰 호출합니다. 실행하면 실제 시그니처가 로그에 찍힙니다.

```
adb logcat -s Cabin4D
# 예) SDK HVAC.setHvacFanSpeed(area: ..., value: kotlin.Int, onComplete: ..., onFailed: ...)
```

- 로그에 `인자를 맞추지 못함`이 보이면, 찍힌 시그니처를 보고 `PleosActuator`의 hints 이름을 맞추세요.
- 시그니처를 확인했으면 **타입 호출로 바꾸는 것을 권장** 합니다(컴파일 검사, 난독화 가능, 기술 활용성 심사에도 유리).
- 앰비언트 색상이 enum 팔레트이거나 모델 클래스면 지금 코드는 밝기만 바꿉니다. 시그니처에 맞게 색 변환을 추가하세요.

## 이펙트 트랙 형식

```json
{
  "id": "my-movie",
  "order": 5,
  "title": "영화 제목",
  "year": 2026,
  "description": "목록 카드에 보일 한두 줄 소개",
  "media": "https://example.com/my-movie.mp4",
  "duration": "01:32:10.0",
  "attribution": "저작자 표시 문구",
  "license": "CC BY 3.0",
  "highlights": [ { "at": "12:00.0", "label": "폭발 장면" } ],
  "cues": [
    { "at": "00:12.0", "wind": 0.9, "hold": 1500, "note": "폭발 돌풍" },
    { "at": "00:12.0", "ambient": "#FF5A1F", "brightness": 1.0, "hold": 1200, "note": "폭발 섬광" },
    { "at": "00:22.0", "direction": "FACE_FLOOR", "note": "눈보라" },
    { "at": "00:00.0", "cabin": "off", "hold": 5400000, "note": "실내등 암전" },
    { "at": "00:12.0", "reading": "on", "hold": 400, "note": "폭발 번쩍임" }
  ]
}
```

| 필드 | 설명 |
| --- | --- |
| `at` | 효과 시작 시각. `"mm:ss.s"`, `"hh:mm:ss.s"`, 또는 ms 숫자 |
| `wind` | 바람 세기 0.0~1.0. 차량의 풍량 범위(capability)로 환산 |
| `direction` | `FACE`, `FLOOR`, `FACE_FLOOR`, `DEFROST` |
| `ambient` + `brightness` | 무드등 색 `#RRGGBB`, 밝기 0.0~1.0 |
| `cabin` | 실내등(천장 가운데 등) `"off"` / `"on"`. 영화 시작 때 `"off"` + `hold`로 크레딧 전까지 암전 |
| `reading` | 독서등(좌석별 맵등) `"on"` / `"off"`. 번개·폭발 순간에 `"on"` + `hold` 400 정도로 짧게 번쩍임 |
| `hold` | (선택) ms 뒤 그 채널을 사용자 원래 설정으로 복귀. 없으면 다음 큐까지 유지 |
| `media` | `https://…` 스트리밍 주소 또는 `asset:///…` 내장 영상. `null`이면 시뮬레이션 모드 |
| `order`, `year`, `description` | 목록 정렬 순서와 카드 정보 |
| `attribution`, `license`, `licenseUrl` | 저작자 표시(카드·재생 화면에 표시) |
| `highlights` | 4D 명장면 바로가기 `{ "at", "label" }` |
| `wind` 0 | 바람을 사용자 원래 설정으로 되돌림 |

큐 한 줄에는 `wind` / `direction` / `ambient` / `cabin` / `reading` 중 하나만 둡니다. 채널(바람·방향·무드등·실내등·독서등)은 서로 독립입니다.
실내등과 독서등은 켜고 끄는 것만 됩니다(색·밝기 없음). 원래 스위치 값을 읽지 못한 차에서는 되돌릴 수 없으므로 효과를 내지 않습니다. 독서등도 무드등처럼 1초에 3번 넘게 바뀌지 않습니다.
`assets/tracks/`에 JSON을 넣으면 목록에 자동으로 나타납니다.

### 트랙 만들기 (tools/track-maker)

1. `tools/track-maker/index.html`을 브라우저로 열고 영상 파일을 고릅니다(파일은 브라우저 안에서만 처리).
2. **분석**: 0.5초마다 프레임을 읽어 밝기·대표색·움직임을 기록하고, 4초 간격 장면 썸네일을 보여 줍니다.
3. **장면 정의**: 썸네일을 보며 바람을 넣을 구간을 적습니다(`scenes/*.json`이 예시). `wind` 기본 세기, `dir` 송풍 방향, `gusts`(움직임이 튀는 순간 자동 돌풍), `keys`(직접 지정한 세기 변화), `flashes`(섬광 시각), `credits`(크레딧 구간).
4. **생성**: 무드등은 2초 창의 화면 색으로 자동 생성합니다. 색은 화면 채도에 비례하고, 어두운 장면은 6초 이상 이어질 때만 바꿔 깜빡임을 줄입니다. 섬광은 0.7초 뒤 장면 조명으로 돌아옵니다. 저장한 JSON을 `assets/tracks/`에 넣으면 끝입니다.

수록된 세 영화의 트랙도 이 도구로 만들었습니다(`scenes/sintel.json`, `tears-of-steel.json`, `big-buck-bunny.json`).

## 안전·차량 설계 포인트

| 항목 | 구현 |
| --- | --- |
| 정차 전용 | `DrivingGate`가 `CarUxRestrictions`의 `UX_RESTRICTIONS_NO_VIDEO`를 받으면 재생 정지 + 효과 복원. 차량에서 규제 상태를 받지 못하면 재생을 막음(fail-closed). 플레이어 기본 컨트롤러를 꺼서 재생은 항상 게이트를 거침 |
| 원래 설정 복원 | 시작 시 풍량·송풍 방향·AUTO·무드등 상태를 저장하고, 일시정지·앱 이탈·종료·효과 OFF·주행 규제 때 되돌림 |
| 오디오 포커스 | 미디어 용도로 오디오 포커스를 요청해 내비 안내 등 차량 오디오 정책을 따름 |
| AUTO 공조 처리 | AUTO가 켜져 있으면 제어기가 수동 풍량을 덮어쓰므로, 바람 효과 동안만 끄고 끝나면 AUTO를 마지막에 복원 |
| 선행 보상 | 블로워 램프업·모드 도어 이동 지연만큼 효과를 미리 실행(`SchedulerConfig.leadMs`: 바람 1.2초, 방향 1.5초, 조명 0.1초 — 예시값, 측정 후 조정) |
| 명령 간격 제한 | 채널별 최소 간격(바람 0.4초, 방향 1초)으로 차량 네트워크·액추에이터 부담 방지 |
| 광과민성 보호 | 무드등 변경은 초당 3회 이하(최소 334ms 간격) |
| 강도 상한 | 기본 강도 0.7 × 최대 바람 상한 0.8 |

## 다음에 붙일 수 있는 4D 효과 (Vehicle SDK 2.3.2 기준 조사)

| 효과 | API(권한) | 장면 예 | 주의 |
| --- | --- | --- | --- |
| 시트 열선 | `setHvacSeatTemperature(area, Int)` (`CONTROL_CAR_SEATS`) | 불길·폭발·모닥불 | 데워지는 데 수십 초 → 선행 큐 필요 |
| 시트 통풍 | `setHvacSeatVentilation(area, Int)` (`CONTROL_CAR_SEATS`) | 눈보라·공포 장면의 서늘함 | 단계 범위는 capability로 확인 |
| 스티어링 열선 | `setHvacSteeringWheelHeat(Int)` (`CONTROL_CAR_CLIMATE`) | 운전석 관객 손에 열기 | 운전석만 |
| 설정 온도 | `setHvacTemperature(area, Float, unit)` (`CONTROL_CAR_CLIMATE`) | 설원은 낮추고 사막은 올리기 | 반응이 느려 장면 단위로만 |
| 실내등·독서등 | `setCabinLightSwitch`, `setReadingLightSwitch` (`CONTROL_CAR_LIGHTS`) | 상영 시작 암전, 번개·총구 섬광 | 빠른 반복 점멸 금지(심사·광과민성) |
| 블라인드·스마트 글라스 | `moveWindowBlind`, `setSmartGlassLevel` (`CONTROL_CAR_WINDOWS`) | 상영 전 암막 | 탑재 차량만, 에뮬레이터 검증 불가 |
| 충전·착석 조회 | `getEvChargeSwitchState`(`CAR_ENERGY`), `getCurrentSeatOccupancy`(`CAR_SEATS`) | 충전 중 상영 모드, 앉은 좌석에만 효과 | 조회 SDK라 지금 바로 사용 가능 |

쓰면 안 되는 것: 외부 조명(전조등·안개등), 도어·창문 개폐, 와이퍼, 충전 제어, 시트 위치를 반복 움직이는 모션 효과(끼임·마모). 출처: Pleos 개발 문서(권한·주행 제한·앱 심사 가이드)와 `Vehicle-2.3.2-sources.jar`.

## 확인 필요 / 알려진 제약

- [x] **차량 제어 API**: Playground에서 Vehicle Control SDK는 '준비 중', Vehicle State(조회) SDK는 신청 없이 사용 가능. 공모전 설명회 안내대로 개발 단계에서는 권한 선언만으로 에뮬레이터에서 확인. 앱 마켓 출시 땐 승인받은 권한만 남길 것
- [ ] 스트리밍 주소(download.blender.org, archive.org)가 바뀌면 트랙 `media`만 고치면 됨. 심사 환경 인터넷 여부 확인
- [ ] **에뮬레이터에서 제어 명령이 반영되지 않음(2026-11-10 확인)**: `4D 효과 테스트` 화면에 차량 값을 다시 읽은 결과가 나온다. 에뮬레이터 v2.1.0에서 본 결과는 아래와 같다.
  - 읽기(capability, 현재 값)는 모두 된다: 풍량 1~8단(앞좌석), 공조 전원(전 좌석), 무드등 밝기 0~100·색(전 좌석), 독서등 4개 좌석, 실내등.
  - 무드등 색·밝기, 공조 전원 켜기는 SDK가 "받았다"고 응답하지만 1.5초 뒤 다시 읽으면 값이 그대로다(색 #FFFFFF, 밝기 70, 전원 꺼짐).
  - 풍량은 공조 전원이 꺼져 있어서 거부된다(`HVAC_FAN_SPEED`, error code 2 = NOT_AVAILABLE). 앱은 바람 효과 전에 전원을 켜려 하지만 위 이유로 켜지지 않는다.
  - 무드등 스위치 ON, 실내등 OFF는 "반영 확인"으로 나오지만 원래 값과 같아서 바뀐 것인지 알 수 없다.
  - `setAmbientColorAutoByDriveModeEnabled`(0x21200787)은 에뮬레이터에 없는 속성이다.
  - 다음 확인: Virtual Console에서 HVAC Power를 직접 켠 뒤 풍량 명령이 통하는지, 제어 명령이 Virtual Console 값에 나타나는지. Virtual Console의 Ambient Color는 Cyan·Blue·Purple·White만 고를 수 있어 임의 색이 안 될 수도 있다. 안 되면 공모전 문의처에 에뮬레이터의 제어 지원 범위를 물어볼 것.
- [ ] 선행 보상·간격 값을 에뮬레이터(가능하면 실차)에서 측정해 조정
- [ ] 외부 영상 앱 연동은 확장 계획: `PlaybackClock`을 하나 더 구현해 미디어 세션의 제목·재생 위치로 동기화(접근 권한·플랫폼 허용 여부 확인 필요)
- [ ] 제출한 APK는 디버그 빌드(디버그 키 서명). 앱 마켓 출시 땐 정식 키스토어로 서명한 release 빌드로 교체

## 라이선스·출처

- 영화: Sintel, Tears of Steel, Big Buck Bunny © Blender Foundation, CC BY 3.0 (위 상영 목록의 표시 문구 참고)
- Sintel 한국어·영어 자막: Blender Foundation 배포본. Tears of Steel 영어 자막: 공식 배포본, 한국어 자막은 이를 옮긴 것
- Vehicle SDK: Pleos Playground 제공

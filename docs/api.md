# API 명세

- 근거: `손끝길 기능 명세서.docx` F-14(매장 메뉴 등록), F-15(익명 사용 통계), 기획서의 모델 배포 API
- 계약: PR #1(FastAPI 시험 구현)과 같은 경로·필드·상태 코드. 앱이 이미 이 형식으로 연동을 확인함
- 직접 호출해 보기: 서버의 `/docs` (Swagger UI)
- 공통: 본문은 JSON, 필드 이름은 `snake_case`

## 공통 규칙

| 상태 코드 | 뜻 | 본문 |
|---|---|---|
| 401 | 점주 키·운영자 키가 없거나 틀림 | `{"detail": "점주 키가 맞지 않습니다"}` |
| 404 | 매장 없음 | `{"detail": "매장을 찾을 수 없습니다"}` |
| 409 | 같은 `event_id`에 다른 통계 내용 | `{"detail": "..."}` |
| 422 | 요청 형식·값 오류 | `{"detail": [{"loc": ["body", "items", 0, "name"], "msg": "...", "type": "..."}]}` 또는 `{"detail": "..."}` |
| 429 | 통계 전송 한도 초과 (IP당 1분 30회) | `{"detail": "잠시 후 다시 보내 주세요"}` |

### 권한

| 헤더 | 누가 | 할 수 있는 것 |
|---|---|---|
| 없음 | 앱·누구나 | 매장 조회, 근처 매장, 메뉴 조회, 통계 보내기, 매장 만들기, 모델 정보 |
| `X-Owner-Key` | 매장을 만든 사람 | 그 매장 정보·메뉴 수정, 그 매장 통계 보기, 매장 삭제 |
| `X-Admin-Key` | 팀 (`ADMIN_KEY` 환경 변수) | 전체 매장 목록, 전체 통계, 모든 매장 수정 |

점주 키는 매장을 만들 때 한 번만 보여 주고, 서버에는 SHA-256 해시만 저장한다. `ADMIN_KEY`가 비어 있으면 운영자 API는 잠긴다.

## 앱이 쓰는 흐름

```
1) 매장 찾기    GET /api/stores/nearby?lat=..&lng=..   (또는 키오스크 옆 스티커의 매장 코드)
2) 메뉴 받기    GET /api/stores/{code}/menu             (ETag로 바뀌었을 때만 다시 받음)
3) 주문 안내    (전부 휴대폰 안에서)
4) 기록 보내기  POST /api/stats/sessions                (주문을 마치거나 그만둘 때 한 번)
```

## 1. 매장 (F-14)

| 메서드·경로 | 권한 | 설명 |
|---|---|---|
| `POST /api/stores` | 없음 | 매장 등록 → 201, 응답에 `owner_key` (한 번만) |
| `GET /api/stores/nearby?lat=&lng=&radius_m=300` | 없음 | 근처 매장 최대 20개, 가까운 순. `radius_m`은 0 초과 5000 이하. 받은 위치는 저장하지 않음 |
| `GET /api/stores` | 운영자 | 전체 매장 목록 |
| `GET /api/stores/{code}` | 없음 | 매장 조회. 코드는 대소문자 구분 없음 |
| `PATCH /api/stores/{code}` | 점주·운영자 | 보낸 필드만 수정. `null`을 보내면 값을 지움. 빈 본문 `{}`은 키 확인용 |
| `DELETE /api/stores/{code}` | 점주·운영자 | 매장과 메뉴 삭제 → 204. 통계 기록은 남음 |

등록 본문 (`name`만 필수):

```json
{"name": "카페 손끝 영대점", "address": "경산시 ...", "lat": 35.83, "lng": 128.75, "kiosk_vendor": "..."}
```

응답:

```json
{"code": "K7M3Q2", "name": "카페 손끝 영대점", "address": "경산시 ...", "lat": 35.83, "lng": 128.75,
 "kiosk_vendor": "...", "menu_version": 0, "updated_at": "2026-10-03T03:25:51Z", "owner_key": "..."}
```

- `code`: 6자리. 헷갈리는 0·O, 1·I 제외
- 근처 매장 응답에는 `distance_m`(미터, 소수 첫째 자리)이 더 붙음

## 2. 메뉴 (F-14)

| 메서드·경로 | 권한 | 설명 |
|---|---|---|
| `GET /api/stores/{code}/menu` | 없음 | 메뉴 사전. `ETag: "{code}-{menu_version}"`. `If-None-Match`가 같으면 304 |
| `PUT /api/stores/{code}/menu` | 점주·운영자 | 메뉴 전체 교체. `menu_version` 1 증가 |

저장 본문 (최대 500개):

```json
{"items": [
  {"category": "커피", "name": "아메리카노", "price": 4500, "aliases": ["아아"],
   "options": [{"group": "온도", "values": ["HOT", "ICE"]}], "sold_out": false}
]}
```

- `name`만 필수. 앞뒤 공백을 지운 뒤 1~80자. 같은 이름이 둘 이상이면 422
- `price`: 0~10,000,000 또는 `null`
- `aliases`: 최대 20개. 공백·빈 값·중복은 서버가 정리
- `options`: 최대 10묶음

응답 (조회와 같은 형식):

```json
{"store_code": "K7M3Q2", "store_name": "카페 손끝 영대점", "menu_version": 1,
 "categories": ["커피"],
 "items": [{"id": 1, "category": "커피", "name": "아메리카노", "price": 4500, "aliases": ["아아"],
            "options": [{"group": "온도", "values": ["HOT", "ICE"]}], "sold_out": false}]}
```

## 3. 익명 사용 통계 (F-15)

### 기록 보내기

`POST /api/stats/sessions` → 201 `{"ok": true}`. 재전송이면 201 `{"ok": true, "duplicate": true}`.

```json
{
  "event_id": "order-session-001", "store_code": "K7M3Q2", "app_version": "0.1.0", "model_version": "2026.10.02",
  "completed": true, "duration_s": 42.5,
  "steps": [
    {"screen_type": "menu", "target_kind": "tab", "result": "success", "reach_s": 3.2, "hints": 4},
    {"screen_type": "menu", "target_kind": "menu", "result": "fail", "reach_s": 6.0, "fail_reason": "no_change"}
  ]
}
```

| 필드 | 값 |
|---|---|
| `event_id` | 선택. 12~80자 영문·숫자·`-`. 주문마다 새로 만드는 값. 같은 값이 다시 오면 한 번만 집계하고, 내용이 다르면 409 |
| `store_code` | 선택. 모르는 코드면 매장 없이 기록만 저장 |
| `completed` | 필수. 결제 화면까지 갔는가 |
| `duration_s` | 필수. 0~7200 |
| `steps` | 최대 200개. 누름 한 번(F-10 판정)마다 하나 |
| `steps[].screen_type` | `menu`, `option`, `cart`, `payment`, `start`, `unknown`(기본) |
| `steps[].target_kind` | `tab`, `menu`, `price`, `button`, `back`, `unknown`(기본) |
| `steps[].result` | 필수. `success`, `fail`, `uncertain`, `restarted`, `abandoned` |
| `steps[].reach_s` | 목표 지정부터 "지금 누르세요"까지 초. 0~600 |
| `steps[].hints` | 이 단계에서 나간 안내 음성 수. 0~1000 |
| `steps[].fail_reason` | 최대 60자. 예: `no_change`, `unexpected:screen_type` |

익명성 규칙 (수용 기준 "개인을 식별할 수 있는 정보 없음"):

- 정의되지 않은 필드(영상·음성·위치·기기 ID 등)가 하나라도 있으면 422로 거부
- `NaN` 같은 유한하지 않은 숫자는 422
- IP는 전송 한도를 세는 데만 메모리에서 1분간 쓰고, DB에는 저장하지 않음

### 집계 보기

`GET /api/stats/summary?store_code=&days=30`

- `store_code`를 주면 그 매장(점주 키), 안 주면 전체(운영자 키)
- `days`: 1~365

```json
{"sessions": 2, "completed_rate": 0.5, "steps": 3, "step_success_rate": 0.667,
 "avg_reach_s": 3.73, "avg_duration_s": 21.25,
 "by_screen": {"menu": {"steps": 2, "success_rate": 0.5, "avg_reach_s": 4.6}},
 "fail_reasons": {"no_change": 1},
 "daily": [{"date": "2026-10-03", "sessions": 2, "completed": 1}]}
```

- `fail_reasons`: 실패 원인 상위 10개. `fail_reason`이 없으면 `result` 값으로 셈
- `daily`: 한국 날짜 기준 (PR #1은 UTC 날짜 기준)

## 4. 모델 정보

`GET /api/models/latest`

```json
{"version": "2026.10.02",
 "files": [{"name": "m1_screen_corners_int8.onnx", "url": null, "sha256": "...", "size_bytes": 6097266}, ...]}
```

- 모델 3개의 SHA-256·크기는 `src/main/resources/web/model-manifest.json`(AI 저장소 파일 기준)에서 읽음
- `url`은 `MODEL_BASE_URL`이 있을 때만 채워짐. `null`이면 앱은 APK에 든 모델을 씀

## 5. 화면·기타

| 경로 | 설명 |
|---|---|
| `/owner` | 점주 메뉴 등록 (매장 만들기, 메뉴 표 편집, 엑셀 붙여넣기) |
| `/dashboard` | 사용 통계 (점주 키 또는 운영자 키) |
| `/kiosk?flow=1` | 시연 키오스크 (2, 3도 지원). 실제 결제 없음 |
| `/docs` | API 문서 (Swagger UI) |
| `/healthz` | `{"ok": true, "db": "mysql"}` |

## 6. 환경 변수

| 이름 | 기본값 | 설명 |
|---|---|---|
| `ADMIN_KEY` | 없음 (운영자 API 잠김) | 운영자 키 |
| `CORS_ORIGINS` | `*` | 브라우저에서 호출할 수 있는 출처, 쉼표 구분 |
| `STATS_RATE_PER_MIN` | `30` | IP당 1분 통계 전송 한도 |
| `MODEL_VERSION` | `2026.10.02` | 앱이 받을 최신 모델 버전 |
| `MODEL_BASE_URL` | 없음 | 모델 파일 주소 (예: GitHub Release) |

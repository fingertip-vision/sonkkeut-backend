# API 명세 (초안)

- 근거: `손끝길 기능 명세서.docx` F-14(매장 메뉴 등록), F-15(익명 사용 통계)
- 상태: 팀 합의 전 초안. 명세서에 API 형태가 없어 설계자가 정한 것
- 공통: 기본 경로 `/api/v1`, 본문은 JSON, 오류는 `{"error": "..."}`

| 상태 코드 | 뜻 |
|---|---|
| 400 | 요청 형식·값 오류 (`invalid field: 필드명`, `malformed request body` 등) |
| 404 | 대상 없음 (`store not found`, `category not found`, `menu not found`) |

## 1. 매장 (F-14)

| 메서드·경로 | 설명 | 응답 |
|---|---|---|
| `POST /stores` | 매장 등록. 본문 `{"name": "손끝 카페"}` | 201 |
| `GET /stores/{storeId}` | 매장 조회 | 200 |

```json
{"id": 1, "name": "손끝 카페", "storeCode": "FKV72Q", "dictionaryVersion": 1}
```

- `storeCode`: 앱이 메뉴 사전을 찾을 때 쓰는 6자리 코드. 헷갈리는 0·O, 1·I 제외
- `dictionaryVersion`: 카테고리·메뉴가 바뀔 때마다 1씩 증가

## 2. 카테고리·메뉴 (F-14)

카테고리는 키오스크 메뉴 화면의 탭("커피" 등)에 대응.

| 메서드·경로 | 설명 | 응답 |
|---|---|---|
| `GET /stores/{storeId}/categories` | 카테고리 목록 | 200 |
| `POST /stores/{storeId}/categories` | 카테고리 등록 | 201 |
| `PUT /stores/{storeId}/categories/{categoryId}` | 카테고리 수정 | 200 |
| `DELETE /stores/{storeId}/categories/{categoryId}` | 카테고리 삭제. 메뉴가 남아 있으면 400 `category not empty` | 204 |
| `GET /stores/{storeId}/menus` | 메뉴 목록 | 200 |
| `POST /stores/{storeId}/menus` | 메뉴 등록 | 201 |
| `PUT /stores/{storeId}/menus/{menuId}` | 메뉴 수정 (전체 교체) | 200 |
| `DELETE /stores/{storeId}/menus/{menuId}` | 메뉴 삭제 | 204 |

카테고리 본문: `{"name": "커피", "sortOrder": 1}` (`sortOrder` 생략 시 0)

메뉴 본문:

```json
{
  "categoryId": 1,
  "name": "아메리카노",
  "price": 4500,
  "soldOut": false,
  "sortOrder": 1,
  "aliases": ["아아", "아메"],
  "options": {"temp": ["hot", "ice"], "size": ["regular", "large"]}
}
```

- 필수: `categoryId`, `name`, `price`(0 이상). 나머지는 생략 가능
- `aliases`: 사용자가 실제로 말하는 이름. F-06 단어 보정용
- `options`: 키는 주문 의도 JSON의 `options` 키와 같은 이름(`temp` 등)

## 3. 메뉴 사전 (앱 → 서버)

`GET /dictionaries/{storeCode}`

```json
{
  "storeName": "손끝 카페",
  "version": 3,
  "categories": [
    {
      "name": "커피",
      "menus": [
        {
          "name": "아메리카노",
          "price": 4500,
          "soldOut": false,
          "aliases": ["아아", "아메"],
          "options": {"temp": ["hot", "ice"]}
        }
      ]
    }
  ]
}
```

- 응답 헤더 `ETag: "3"`(버전). 앱이 `If-None-Match: "3"`을 보내고 버전이 같으면 본문 없이 304
- 앱은 받은 사전을 캐시해서 쓰고, 조회에 실패해도 사전 없이 주문을 계속함

## 4. 익명 사용 통계 (F-15)

### 기록 전송

`POST /stats/sessions` → 204. 주문 한 번이 끝난 뒤 앱이 한 번에 전송.

```json
{
  "sessionId": "0b0e5c2e-6f0c-4c1e-9d7a-2f6a3a1b9c11",
  "storeCode": "FKV72Q",
  "appVersion": "0.1.0",
  "result": "FAILED",
  "endState": "SE",
  "durationMs": 42000,
  "errors": [
    {"state": "S5", "kind": "WRONG_PRESS", "elapsedMs": 30000}
  ]
}
```

| 필드 | 값 |
|---|---|
| `sessionId` | 주문마다 앱이 새로 만드는 무작위 UUID. 기기에 저장하지 않음. 같은 값이 다시 오면 한 번만 집계 |
| `storeCode` | 선택. 모르면 생략. 틀린 코드여도 기록은 저장 |
| `result` | `COMPLETED`(결제 화면 도달), `ABANDONED`(사용자가 그만둠), `FAILED`(복구 못 하고 끝남) |
| `endState` | 끝난 시점의 상태. `S0`~`S6`, `SE` |
| `errors[].kind` | `SCREEN_GLARE`, `HAND_LOST`, `WRONG_PRESS`, `SOLD_OUT`, `TIMEOUT`, `PAYMENT_REACHED` |
| `elapsedMs` | 주문 시작부터 경과한 시간 |

익명성 규칙 (수용 기준 "개인을 식별할 수 있는 정보 없음"):

- 받지 않는 것: 사용자·기기 식별자, 영상, 음성, 발화 내용, 주문한 메뉴, 위치
- 서버는 요청의 IP를 저장하지 않음

### 집계 조회

`GET /stats/summary`

```json
{
  "totalSessions": 3,
  "completedSessions": 1,
  "successRate": 0.33,
  "averageCompletedDurationMs": 83000,
  "failuresByState": {"SE": 2},
  "errorsByKind": {"WRONG_PRESS": 2, "TIMEOUT": 2}
}
```

- `failuresByState`: 완료하지 못한 주문이 끝난 상태(실패 지점)

## 5. 미정 사항

- 인증: 현재 모든 API가 인증 없이 열려 있음. 점주 계정 필요 여부가 정해지면 매장·메뉴 등록과 집계 조회에 적용
- 앱이 매장 코드를 얻는 방법 (직접 입력, QR 등)
- F-11 예외 7종 중 나머지 한 가지 → `errors[].kind`에 추가
- 메뉴판 사진으로 등록하는 방식이 필요한지

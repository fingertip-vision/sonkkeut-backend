# sonkkeut-backend · 손끝길 백엔드

손끝길의 AI는 휴대폰 안에서 돕니다. 이 서버는 **AI 계산을 하지 않고**, 여러 사용자와 점주가 함께 쓰는 데이터만 맡습니다.
서버가 꺼져 있어도 앱의 주문 안내는 그대로 동작합니다.

| 기능 | 내용 | API |
| --- | --- | --- |
| F-14 매장 메뉴 등록 | 점주가 웹에서 메뉴를 올리면, 앱이 그 매장의 메뉴 사전을 받아 글자 인식(F-04)·음성 주문(F-06)을 보정 | `/api/stores…` |
| F-15 익명 사용 통계 | 앱이 주문 한 번마다 단계별 성공 여부·걸린 시간만 보냄 (영상·음성·위치·기기 정보 없음) | `/api/stats…` |
| 모델 버전 안내 | 앱이 새 모델이 있는지 확인 | `/api/models/latest` |
| 점주 웹 | 매장 만들기, 메뉴 표 편집(엑셀 붙여넣기), 매장 통계 | `/owner`, `/dashboard` |

API 문서는 서버의 `/docs`에서 바로 시험해 볼 수 있습니다 (FastAPI 자동 문서).

## 앱에서 쓰는 흐름

```
1) 매장 찾기   GET /api/stores/nearby?lat=..&lng=..      (또는 키오스크 옆 스티커의 매장 코드)
2) 메뉴 받기   GET /api/stores/{code}/menu               (ETag로 바뀌었을 때만 다시 받음)
               → items: [{name, price, aliases, options, sold_out}]  → 노현석 모듈의 OCR·음성 보정 사전으로
3) 주문 안내   (전부 휴대폰 안에서)
4) 기록 보내기 POST /api/stats/sessions                  (주문을 마치거나 그만둘 때 한 번)
```

`POST /api/stats/sessions` 예시 — 앱의 누름 결과 판정(`verdict`)을 그대로 모으면 됩니다.

```json
{
  "store_code": "EWHZ33", "app_version": "0.1.0", "model_version": "2026.10.02",
  "completed": true, "duration_s": 42.5,
  "steps": [
    {"screen_type": "menu", "target_kind": "tab", "result": "success", "reach_s": 3.2, "hints": 4},
    {"screen_type": "menu", "target_kind": "menu", "result": "fail", "reach_s": 6.0, "fail_reason": "no_change"}
  ]
}
```

## 권한

| 키 | 누가 | 할 수 있는 것 |
| --- | --- | --- |
| 없음 | 앱·누구나 | 매장 조회, 근처 매장, 메뉴 조회, 통계 보내기, 매장 만들기 |
| 점주 키 (`X-Owner-Key`) | 매장을 만든 사람 | 그 매장 정보·메뉴 수정, 그 매장 통계 보기, 매장 삭제 |
| 운영자 키 (`X-Admin-Key`) | 팀 | 전체 매장 목록, 전체 통계, 모든 매장 수정 |

점주 키는 매장을 만들 때 **한 번만** 보여 주고 서버에는 해시만 저장합니다. 통계 보내기는 IP당 1분에 30번으로 제한합니다.

## 로컬 실행

```bash
pip install -r requirements-dev.txt
ADMIN_KEY=local-admin uvicorn app.main:app --reload     # Windows는 run_local.bat 더블클릭
python -m pytest -q                                      # 테스트 (SQLite)
```

DB는 `DATABASE_URL`이 없으면 `sonkkeut.db`(SQLite) 파일을 쓰고, 있으면 Postgres를 씁니다. 테이블은 시작할 때 자동으로 만듭니다.
테스트는 SQLite와 Postgres 16 양쪽에서 통과했습니다.

## 배포 (Render, 무료)

`render.yaml`(Blueprint)에 웹 서비스와 Postgres가 정의되어 있습니다.

1. Render 대시보드 → **New → Blueprint** → `fingertip-vision/sonkkeut-backend` 선택 → **Apply**
2. 웹 서비스·DB가 만들어지고 `DATABASE_URL`은 자동 연결, `ADMIN_KEY`는 자동 생성됩니다
   (Environment 탭에서 확인·변경)
3. `https://<서비스 주소>/healthz` 가 `{"ok": true, "db": "postgres"}` 이면 끝

무료 플랜 주의: 15분간 요청이 없으면 서버가 잠들어 첫 요청에 약 50초 걸립니다(시연 직전에 한 번 열어 두기).
무료 Postgres는 만든 지 30일 뒤 만료되므로, 대회 이후에도 쓰려면 유료로 바꾸거나 Neon 같은 무료 DB 주소를 `DATABASE_URL`에 넣으세요.
앱은 서버가 꺼져 있어도 주문 안내가 그대로 동작합니다.

## 배포 (Railway)

1. Railway에서 **New Project → Deploy from GitHub repo → `fingertip-vision/sonkkeut-backend`**
2. 같은 프로젝트에 **+ New → Database → PostgreSQL** 추가
3. 백엔드 서비스의 **Variables**
   - `DATABASE_URL` = `${{Postgres.DATABASE_URL}}` (Postgres 서비스 참조)
   - `ADMIN_KEY` = 팀만 아는 긴 문자열
4. **Settings → Networking → Generate Domain** 으로 공개 주소 만들기
5. `https://<주소>/healthz` 가 `{"ok": true, "db": "postgres"}` 이면 끝

`railway.json`과 `Dockerfile`이 들어 있어 빌드·실행 명령은 따로 넣지 않아도 됩니다. 이후 `main` 브랜치에 push하면 자동으로 다시 배포됩니다.

## 환경변수

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `DATABASE_URL` | SQLite 파일 | Postgres 주소 (`postgres://` 형식도 자동 변환) |
| `ADMIN_KEY` | 없음(운영자 API 잠김) | 운영자 키 |
| `MODEL_VERSION` | `2026.10.02` | 앱이 받을 최신 모델 버전 |
| `MODEL_BASE_URL` | 없음 | 모델 파일 주소 (예: GitHub Release) |
| `CORS_ORIGINS` | `*` | 웹 화면 허용 출처 |
| `STATS_RATE_PER_MIN` | `30` | IP당 1분 통계 전송 한도 |

## 폴더

```
app/main.py      API (매장·메뉴·통계·모델), 점주 웹 연결
app/db.py        테이블: stores, menu_items, usage_sessions
app/schemas.py   요청·응답 형식과 검증
app/static/      점주 메뉴 등록(owner), 사용 통계(dashboard) 화면
tests/           API 테스트
```

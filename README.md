# sonkkeut-backend · 손끝길 백엔드

손끝길의 AI는 휴대폰 안에서 돕니다. 이 서버는 **AI 계산을 하지 않고**, 여러 사용자와 점주가 함께 쓰는 데이터만 맡습니다.
서버가 꺼져 있어도 앱의 주문 안내는 그대로 동작합니다.

| 기능 | 내용 | API |
| --- | --- | --- |
| F-14 매장 메뉴 등록 | 점주가 웹에서 메뉴를 올리면, 앱이 그 매장의 메뉴 사전을 받아 글자 인식(F-04)·음성 주문(F-06)을 보정 | `/api/stores…` |
| F-15 익명 사용 통계 | 앱이 주문 한 번마다 단계별 성공 여부·걸린 시간만 보냄 (영상·음성·위치·기기 정보 없음) | `/api/stats…` |
| 모델 버전 안내 | 앱이 새 모델이 있는지 확인 | `/api/models/latest` |
| 점주 웹 | 매장 만들기, 메뉴 표 편집(엑셀 붙여넣기), 매장 통계 | `/owner`, `/dashboard` |
| 시연 키오스크 | 메뉴·옵션·장바구니·결제 화면의 3개 배치, 실제 결제 없음 | `/kiosk?flow=1` (2, 3도 지원) |
| 상세 시뮬레이션 | 단계별 입력·판정·안내·오류 복구를 로컬 브라우저에서 확인 | `/simulation` |

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
  "event_id": "order-session-001", "store_code": "EWHZ33", "app_version": "0.1.0", "model_version": "2026.10.02",
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

```powershell
# 이 작업 폴더(fingertip-vision)에서 실행
powershell -ExecutionPolicy Bypass -File .\outputs\start-local.ps1
# 종료
powershell -ExecutionPolicy Bypass -File .\outputs\stop-local.ps1
# 테스트 (자동으로 분리된 임시 SQLite DB 사용)
Push-Location .\outputs\sonkkeut-backend
..\..\work\backend-venv\Scripts\python.exe -m pytest -q
Pop-Location
```

작업용 스크립트는 `127.0.0.1:18080`에 서버를 열고, `work/sonkkeut-local.db`를 유지합니다. 운영자 키와 프로세스 기록·로그도 `work/`에 보관합니다. 스크립트의 실행파일·PID·시작시각 검증을 통과한 서버 프로세스 트리만 종료합니다.

독립적으로 실행할 때 DB는 `DATABASE_URL`이 없으면 `sonkkeut.db`(SQLite) 파일을 쓰고, 있으면 Postgres를 씁니다. 테이블은 시작할 때 자동으로 만듭니다. 자세한 Android 설치·시연 순서는 상위 폴더의 `LOCAL_GUIDE.md`를 참고하세요.

이번 변경은 API·공개 배포 설정 테스트 24개를 통과했습니다. PostgreSQL을 사용하는 실제 배포와 Linux Docker 빌드는 배포 시 별도로 검증해야 합니다. `requirements.lock.txt`와 `requirements-dev.lock.txt`에는 이 로컬 Python 3.12.14 환경에서 검사한 패키지 버전을 기록했습니다. 로컬 DB·키 파일은 클라우드에 복사하지 않습니다.

### 모바일 계약

통계 세션에 `event_id`를 넣으면 네트워크 복구 후 같은 세션을 재전송해도 한 번만 집계합니다. 같은 ID에 다른 내용을 보내면 409를 반환합니다. 영상·음성·위치·기기 ID 같은 미정의 필드는 422로 거부하고, NaN 통계 및 공백 메뉴 이름도 허용하지 않습니다.

잘못된 매장 이름·옵션 값은 저장 전에 422로 거부합니다. 위도나 경도만 등록한 매장은 주변 검색에서 제외하고, 경도 ±180° 및 고위도 주변 검색도 처리합니다. 통계 제한은 서버가 확인한 요청 주소를 사용하고 429에는 `Retry-After`를 반환합니다. 로컬 실행 스크립트는 프록시 헤더 해석을 끕니다. 이 메모리 제한기는 프로세스 하나에만 적용됩니다.

`GET /api/models/latest`는 APK에 포함한 모델 3개의 이름·버전·SHA-256·크기를 반환합니다. 모델 정보는 `app/static/model-manifest.json`과 AI 저장소의 파일이 일치합니다. 로컬에서는 모델 다운로드 URL이 null이고 앱은 번들 모델을 사용합니다.

시연 매장은 `python scripts/seed_demo.py --url http://127.0.0.1:18080 --credentials /LOCAL/PRIVATE/demo-store.json`으로 만들 수 있습니다. `--credentials`는 저장소 밖의 비공개 경로로 지정하세요. 매장 키는 이 파일에만 저장하며 콘솔에는 코드·메뉴 버전·항목 수만 출력합니다.

## 공개 배포 (Render)

`render.yaml`(Blueprint)에 웹 서비스와 Postgres가 정의되어 있습니다.

1. Render 대시보드 → **New → Blueprint** → `fingertip-vision/sonkkeut-backend`의 검증한 배포 브랜치를 선택합니다. 이미 있는 같은 이름의 서비스·DB가 변경되는지 확인한 뒤 생성합니다.
2. 웹과 DB 모두 `free`와 `singapore`를 명시합니다. `DATABASE_URL`은 DB 참조로 연결되고 `ADMIN_KEY`는 새 값으로 생성됩니다. 로컬 키나 SQLite 파일을 업로드하지 않습니다.
3. `APP_ENV=production`으로 시작합니다. Postgres 연결·32자 이상 운영자 키·명시적 호스트가 없으면 시작을 거부합니다. Render가 제공하는 `RENDER_EXTERNAL_HOSTNAME`을 기본 허용 호스트로 사용합니다. [Render 기본 환경변수](https://render.com/docs/environment-variables)
4. 공개 웹 화면은 같은 서비스에서 제공하므로 `CORS_ORIGINS`는 빈 값입니다. Android 앱도 CORS 예외가 필요하지 않습니다. 다른 웹 사이트를 연결할 때만 정확한 HTTPS 출처를 추가합니다.
5. 루트 `/`는 `/simulation`으로 이동합니다. 실제 결제·카메라·AI 추론을 수행하지 않는 체험 화면입니다. `/healthz`가 `{"ok": true, "db": "postgres"}`인지 확인하고 점주·전체 통계는 키 없이 401인지 확인합니다.

Free 웹 서비스는 15분 무요청 후 잠들며 재기동에 약 1분이 걸릴 수 있습니다. 파일시스템은 배포·재시작·잠들 때 초기화되므로 SQLite를 공개 배포에 사용하지 않습니다. Free Postgres는 워크스페이스당 1개, 1GB이며 생성 30일 후 만료됩니다. 백업도 제공하지 않습니다. 장기 운영과 요금은 실제 계정의 계획을 별도로 확인해야 합니다. [Render 무료 서비스 제약](https://render.com/docs/free)

Blueprint의 DB는 외부 IP 접근을 비활성화하고 같은 지역 서비스가 내부 연결을 사용합니다. 자동 배포는 `autoDeployTrigger: 'off'`로 두어 검증한 커밋을 수동 배포합니다. [Render Blueprint 공식 참조](https://render.com/docs/blueprint-spec)

Docker는 고정 버전 의존성을 설치하고 비root 계정으로 실행합니다. `PORT`가 없으면 10000을 사용하며 요청 본문은 JSON 파싱 전에 2MB로 제한합니다. 점주 키를 반환하는 생성 응답과 권한이 필요한 API 응답은 `Cache-Control: no-store`로 제공합니다. 운영자·점주 키는 요청 헤더에만 넣으세요.

프록시 헤더를 무조건 신뢰해 IP를 위조할 수 없도록 Docker도 `--no-proxy-headers`로 실행합니다. 따라서 배포 뒤 통계 한도는 프록시의 연결 주소를 공유하는 요청 묶음에 적용될 수 있습니다(분당 120개). 현재 무료 시연용 1개 프로세스 구성입니다. 여러 인스턴스로 확장할 때는 신뢰 프록시 범위와 공유 제한 저장소를 별도로 설정해야 합니다.

시연 매장도 배포 후 새로 등록하세요. 기존 `BYDHTF` 매장이나 로컬 점주 키는 이전하지 않습니다. API로 등록하면 생성한 계정만 받는 새 매장 코드와 키를 저장소 밖의 비공개 파일에 보관합니다.

## 환경변수

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `APP_ENV` | `local` (Docker는 `production`) | production에서 안전한 DB·키·호스트·CORS 필수 검증 |
| `DATABASE_URL` | 로컬은 SQLite 파일 | production은 Postgres 필수 (`postgres://`도 자동 변환) |
| `ADMIN_KEY` | 로컬은 없음(운영자 API 잠김) | production은 32자 이상 운영자 키 필수 |
| `MODEL_VERSION` | `2026.10.02` | 앱이 받을 최신 모델 버전 |
| `MODEL_BASE_URL` | 없음 | 모델 파일 주소 (예: GitHub Release) |
| `CORS_ORIGINS` | 로컬 `*`, production 빈 값 | 추가하는 경우 정확한 HTTPS 웹 출처 |
| `ALLOWED_HOSTS` | 로컬 `*`, production Render 호스트 | 쉼표 구분; 다른 호스트·커스텀 도메인을 사용할 때 명시 |
| `MAX_REQUEST_BYTES` | `2097152` | JSON 파싱 전 요청 본문 크기 한도 |
| `STATS_RATE_PER_MIN` | 로컬 `30`, Blueprint `120` | 서버가 확인한 연결 주소당 1분 통계 전송 한도 |

## 폴더

```
app/main.py      API (매장·메뉴·통계·모델), 점주 웹 연결
app/db.py        테이블: stores, menu_items, usage_sessions
app/schemas.py   요청·응답 형식과 검증
app/static/      점주 메뉴 등록(owner), 사용 통계(dashboard) 화면
tests/           API 테스트
```

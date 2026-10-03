"""
손끝길 백엔드 (FastAPI)

AI는 휴대폰 안에서 돈다. 이 서버는 AI 계산을 하지 않고 아래 세 가지만 맡는다.
  F-14 매장 메뉴 등록   점주가 메뉴를 올리면, 앱이 그 매장 메뉴 사전을 받아 글자 인식·음성 주문을 보정한다
  F-15 익명 사용 통계   성공률·실패 지점을 영상 없이 모은다 (점주·운영자 대시보드)
  모델 버전 안내        앱이 새 모델이 있는지 확인한다

API 문서: /docs (자동 생성)
"""
import hashlib
import hmac
import math
import secrets
import time
import json
from contextlib import asynccontextmanager
from collections import Counter, defaultdict, deque
from datetime import datetime, timedelta, timezone
from pathlib import Path
from threading import Lock

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request, Response
from fastapi.encoders import jsonable_encoder
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from starlette.middleware.trustedhost import TrustedHostMiddleware
from sqlalchemy import select
from sqlalchemy.orm import Session
from sqlalchemy.exc import IntegrityError

from . import config
from .db import MenuItem, SessionLocal, Store, UsageSession, UsageReceipt, init_db, now
from .schemas import (MenuItemOut, MenuOut, MenuReplace, ModelInfo, NearbyStore, SessionIn, StoreCreate,
                      StoreCreated, StoreOut, StoreUpdate, Summary)
from .security import RequestBodyLimitMiddleware, SecurityHeadersMiddleware

@asynccontextmanager
async def lifespan(_app):
    init_db()
    yield


app = FastAPI(title="손끝길 백엔드", version="0.1.0", lifespan=lifespan,
              description="매장 메뉴 등록(F-14), 익명 사용 통계(F-15), 모델 버전 안내. AI 계산은 휴대폰에서 한다.")
app.add_middleware(CORSMiddleware, allow_origins=config.CORS_ORIGINS, allow_methods=["*"], allow_headers=["*"])
app.add_middleware(RequestBodyLimitMiddleware)
app.add_middleware(TrustedHostMiddleware, allowed_hosts=config.ALLOWED_HOSTS, www_redirect=False)
app.add_middleware(SecurityHeadersMiddleware)
STATIC = Path(__file__).parent / "static"
app.mount("/static", StaticFiles(directory=STATIC), name="static")


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


# ---------- 공통 ----------
CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # 헷갈리는 0/O, 1/I 제외


def _hash(key: str) -> str:
    return hashlib.sha256(key.encode()).hexdigest()


def _new_code(db: Session) -> str:
    for _ in range(20):
        code = "".join(secrets.choice(CODE_ALPHABET) for _ in range(6))
        if db.scalar(select(Store.id).where(Store.code == code)) is None:
            return code
    raise HTTPException(500, "매장 코드를 만들지 못했습니다")


def _store(db: Session, code: str) -> Store:
    s = db.scalar(select(Store).where(Store.code == code.upper()))
    if s is None:
        raise HTTPException(404, "매장을 찾을 수 없습니다")
    return s


def _is_admin(key: str | None) -> bool:
    return bool(config.ADMIN_KEY) and key is not None and hmac.compare_digest(key.encode(), config.ADMIN_KEY.encode())


def _require_owner(s: Store, owner_key: str | None, admin_key: str | None = None):
    if _is_admin(admin_key):
        return
    if not owner_key or not hmac.compare_digest(_hash(owner_key), s.owner_key_hash):
        raise HTTPException(401, "점주 키가 맞지 않습니다")


def _store_out(s: Store) -> dict:
    return dict(code=s.code, name=s.name, address=s.address, lat=s.lat, lng=s.lng, kiosk_vendor=s.kiosk_vendor,
                menu_version=s.menu_version, updated_at=s.updated_at)


def _haversine_m(lat1, lng1, lat2, lng2):
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    # Antipodal points can round to slightly above 1 in floating point.
    return 2 * r * math.asin(math.sqrt(min(1.0, max(0.0, a))))


@app.get("/healthz", tags=["기타"])
def healthz(db: Session = Depends(get_db)):
    db.scalar(select(1))
    return {"ok": True, "db": "postgres" if "postgresql" in config.DATABASE_URL else "sqlite"}


@app.get("/", include_in_schema=False)
def root():
    return RedirectResponse("/simulation")


@app.get("/owner", include_in_schema=False)
def owner_page():
    return FileResponse(STATIC / "owner.html")


@app.get("/dashboard", include_in_schema=False)
def dashboard_page():
    return FileResponse(STATIC / "dashboard.html")


@app.get("/kiosk", include_in_schema=False)
def kiosk_page():
    return FileResponse(STATIC / "kiosk.html")


@app.get("/simulation", include_in_schema=False)
def simulation_page():
    return FileResponse(STATIC / "simulation.html")


# ---------- 매장 (F-14) ----------
@app.post("/api/stores", response_model=StoreCreated, status_code=201, tags=["매장"])
def create_store(body: StoreCreate, db: Session = Depends(get_db)):
    """매장 등록. 응답의 owner_key는 다시 볼 수 없으니 점주가 보관해야 한다."""
    key = secrets.token_urlsafe(18)
    s = Store(code=_new_code(db), owner_key_hash=_hash(key), **body.model_dump())
    db.add(s)
    db.commit()
    return {**_store_out(s), "owner_key": key}


@app.get("/api/stores/nearby", response_model=list[NearbyStore], tags=["매장"])
def nearby(lat: float = Query(ge=-90, le=90), lng: float = Query(ge=-180, le=180),
           radius_m: float = Query(300, gt=0, le=5000), db: Session = Depends(get_db)):
    """앱이 현재 위치 근처 매장을 찾을 때. 위치는 저장하지 않는다."""
    angular_radius = radius_m / 6_371_000.0
    latitude_delta = math.degrees(angular_radius)
    query = select(Store).where(Store.lat.is_not(None), Store.lng.is_not(None),
                                Store.lat.between(max(-90, lat - latitude_delta), min(90, lat + latitude_delta)))
    # Longitude degrees shrink near the poles; the box must also wrap at ±180°.
    if abs(lat) + latitude_delta < 90:
        longitude_delta = math.degrees(math.asin(min(1.0, math.sin(angular_radius) / math.cos(math.radians(lat)))))
        west, east = lng - longitude_delta, lng + longitude_delta
        if west < -180:
            query = query.where((Store.lng >= west + 360) | (Store.lng <= east))
        elif east > 180:
            query = query.where((Store.lng >= west) | (Store.lng <= east - 360))
        else:
            query = query.where(Store.lng.between(west, east))
    rows = db.scalars(query).all()
    out = []
    for s in rows:
        dist = _haversine_m(lat, lng, s.lat, s.lng)
        if dist <= radius_m:
            out.append({**_store_out(s), "distance_m": round(dist, 1)})
    return sorted(out, key=lambda x: x["distance_m"])[:20]


@app.get("/api/stores", response_model=list[StoreOut], tags=["매장"])
def list_stores(x_admin_key: str | None = Header(None), db: Session = Depends(get_db)):
    """전체 매장 목록 (운영자)"""
    if not _is_admin(x_admin_key):
        raise HTTPException(401, "운영자 키가 필요합니다")
    return [_store_out(s) for s in db.scalars(select(Store).order_by(Store.id)).all()]


@app.get("/api/stores/{code}", response_model=StoreOut, tags=["매장"])
def get_store(code: str, db: Session = Depends(get_db)):
    return _store_out(_store(db, code))


@app.patch("/api/stores/{code}", response_model=StoreOut, tags=["매장"])
def update_store(code: str, body: StoreUpdate, x_owner_key: str | None = Header(None),
                 x_admin_key: str | None = Header(None), db: Session = Depends(get_db)):
    s = _store(db, code)
    _require_owner(s, x_owner_key, x_admin_key)
    for k, v in body.model_dump(exclude_unset=True).items():
        setattr(s, k, v)
    db.commit()
    return _store_out(s)


@app.delete("/api/stores/{code}", status_code=204, tags=["매장"])
def delete_store(code: str, x_owner_key: str | None = Header(None), x_admin_key: str | None = Header(None),
                 db: Session = Depends(get_db)):
    s = _store(db, code)
    _require_owner(s, x_owner_key, x_admin_key)
    db.delete(s)
    db.commit()


# ---------- 메뉴 (F-14) ----------
def _menu_out(s: Store) -> dict:
    items = [MenuItemOut(id=i.id, category=i.category, name=i.name, price=i.price, aliases=i.aliases or [],
                         options=i.options or [], sold_out=i.sold_out) for i in s.items]
    cats = []
    for i in items:
        if i.category and i.category not in cats:
            cats.append(i.category)
    return dict(store_code=s.code, store_name=s.name, menu_version=s.menu_version, categories=cats, items=items)


@app.get("/api/stores/{code}/menu", response_model=MenuOut, tags=["메뉴"])
def get_menu(code: str, request: Request, db: Session = Depends(get_db)):
    """앱이 받는 메뉴 사전. If-None-Match에 menu_version을 주면 바뀌지 않았을 때 304"""
    s = _store(db, code)
    etag = f'"{s.code}-{s.menu_version}"'
    if request.headers.get("if-none-match") == etag:
        return Response(status_code=304, headers={"ETag": etag})
    return JSONResponse(jsonable_encoder(_menu_out(s)), headers={"ETag": etag, "Cache-Control": "no-cache"})


@app.put("/api/stores/{code}/menu", response_model=MenuOut, tags=["메뉴"])
def replace_menu(code: str, body: MenuReplace, x_owner_key: str | None = Header(None),
                 x_admin_key: str | None = Header(None), db: Session = Depends(get_db)):
    """메뉴 전체 교체 (점주 웹의 '저장'). 메뉴 버전이 1 올라가 앱이 새로 받는다."""
    s = _store(db, code)
    _require_owner(s, x_owner_key, x_admin_key)
    names = [i.name for i in body.items]
    dup = [n for n, c in Counter(names).items() if c > 1]
    if dup:
        raise HTTPException(422, f"같은 이름의 메뉴가 있습니다: {', '.join(dup[:5])}")
    s.items.clear()
    db.flush()
    for k, it in enumerate(body.items):
        s.items.append(MenuItem(category=it.category, name=it.name, price=it.price, aliases=it.aliases,
                                options=[o.model_dump() for o in it.options], sold_out=it.sold_out, sort=k))
    s.menu_version += 1
    s.updated_at = now()
    db.commit()
    db.refresh(s)
    return _menu_out(s)


# ---------- 익명 통계 (F-15) ----------
_rate: dict[str, deque] = defaultdict(deque)
_rate_lock = Lock()
_rate_last_cleanup = 0.0
_RATE_MAX_CLIENTS = 10_000


def _rate_limit(request: Request):
    global _rate_last_cleanup
    # Proxy headers are interpreted by the configured ASGI server, never by this
    # handler: an arbitrary caller must not reset its quota by changing XFF.
    ip = request.client.host if request.client else "?"
    t = time.monotonic()
    with _rate_lock:
        if t - _rate_last_cleanup >= 60:
            for client_ip, timestamps in list(_rate.items()):
                while timestamps and t - timestamps[0] >= 60:
                    timestamps.popleft()
                if not timestamps:
                    del _rate[client_ip]
            _rate_last_cleanup = t
        if ip not in _rate and len(_rate) >= _RATE_MAX_CLIENTS:
            raise HTTPException(429, "잠시 후 다시 보내 주세요", headers={"Retry-After": "60"})
        q = _rate[ip]
        while q and t - q[0] >= 60:
            q.popleft()
        if len(q) >= config.STATS_RATE_PER_MIN:
            wait_s = max(1, math.ceil(60 - (t - q[0])))
            raise HTTPException(429, "잠시 후 다시 보내 주세요", headers={"Retry-After": str(wait_s)})
        q.append(t)


@app.post("/api/stats/sessions", status_code=201, tags=["통계"])
def post_session(body: SessionIn, request: Request, db: Session = Depends(get_db)):
    """앱이 주문 한 번을 마치거나 그만둘 때 보낸다. 개인을 식별할 수 있는 정보는 받지 않는다."""
    _rate_limit(request)
    payload_hash = hashlib.sha256(json.dumps(body.model_dump(exclude={"event_id"}), sort_keys=True).encode()).hexdigest()
    if body.event_id:
        receipt = db.get(UsageReceipt, body.event_id)
        if receipt:
            if receipt.payload_hash != payload_hash:
                raise HTTPException(409, "다른 통계에 같은 이벤트 ID를 사용할 수 없습니다")
            return {"ok": True, "duplicate": True}
    code = body.store_code.upper() if body.store_code else None
    if code and db.scalar(select(Store.id).where(Store.code == code)) is None:
        code = None  # 모르는 매장 코드는 버리고 기록만 남긴다
    # Insert only after lookups: autoflush during a lookup must not bypass the
    # commit's duplicate-retry handling when two requests arrive together.
    if body.event_id:
        db.add(UsageReceipt(event_id=body.event_id, payload_hash=payload_hash))
    db.add(UsageSession(store_code=code, app_version=body.app_version, model_version=body.model_version,
                        completed=body.completed, duration_s=body.duration_s, n_steps=len(body.steps),
                        steps=[s.model_dump() for s in body.steps]))
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        receipt = db.get(UsageReceipt, body.event_id) if body.event_id else None
        if receipt and receipt.payload_hash == payload_hash:
            return {"ok": True, "duplicate": True}
        raise HTTPException(409, "통계 기록 충돌")
    return {"ok": True}


def _mean(v):
    v = [x for x in v if x is not None]
    return round(sum(v) / len(v), 2) if v else None


@app.get("/api/stats/summary", response_model=Summary, tags=["통계"])
def summary(store_code: str | None = None, days: int = Query(30, ge=1, le=365), x_owner_key: str | None = Header(None),
            x_admin_key: str | None = Header(None), db: Session = Depends(get_db)):
    """통계 요약. 매장 코드를 주면 그 매장(점주 키 필요), 안 주면 전체(운영자 키 필요)."""
    if store_code:
        s = _store(db, store_code)
        _require_owner(s, x_owner_key, x_admin_key)
    elif not _is_admin(x_admin_key):
        raise HTTPException(401, "전체 통계는 운영자 키가 필요합니다")
    since = datetime.now(timezone.utc) - timedelta(days=days)
    q = select(UsageSession).where(UsageSession.created_at >= since)
    if store_code:
        q = q.where(UsageSession.store_code == store_code.upper())
    rows = db.scalars(q).all()
    steps = [st for r in rows for st in (r.steps or [])]
    by_screen = defaultdict(lambda: {"steps": 0, "success": 0, "reach": []})
    fails = Counter()
    for st in steps:
        b = by_screen[st.get("screen_type", "unknown")]
        b["steps"] += 1
        b["success"] += st.get("result") == "success"
        b["reach"].append(st.get("reach_s"))
        if st.get("result") != "success":
            fails[st.get("fail_reason") or st.get("result")] += 1
    daily = defaultdict(lambda: [0, 0])
    for r in rows:
        d = r.created_at.date().isoformat()
        daily[d][0] += 1
        daily[d][1] += r.completed
    n_ok = sum(1 for st in steps if st.get("result") == "success")
    return Summary(
        sessions=len(rows),
        completed_rate=round(sum(r.completed for r in rows) / len(rows), 3) if rows else None,
        steps=len(steps),
        step_success_rate=round(n_ok / len(steps), 3) if steps else None,
        avg_reach_s=_mean([st.get("reach_s") for st in steps]),
        avg_duration_s=_mean([r.duration_s for r in rows]),
        by_screen={k: {"steps": v["steps"], "success_rate": round(v["success"] / v["steps"], 3),
                       "avg_reach_s": _mean(v["reach"])} for k, v in by_screen.items()},
        fail_reasons=dict(fails.most_common(10)),
        daily=[{"date": d, "sessions": v[0], "completed": v[1]} for d, v in sorted(daily.items())],
    )


# ---------- 모델 버전 ----------
@app.get("/api/models/latest", response_model=ModelInfo, tags=["모델"])
def latest_model():
    """앱에 포함된 모델과 서버 버전을 비교하는 정보. 현재 앱은 APK에 포함된 모델을 사용한다."""
    names = ["m1_screen_corners_int8.onnx", "m2_screen_elements_int8.onnx", "m1r_corner_refiner.onnx"]
    manifest_path = Path(__file__).parent / "static" / "model-manifest.json"
    packaged = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {}
    records = {m["name"]: m for m in packaged.get("models", [])}
    files = [{"name": n, "url": f"{config.MODEL_BASE_URL.rstrip('/')}/{n}" if config.MODEL_BASE_URL else None,
              "sha256": records.get(n, {}).get("sha256"), "size_bytes": records.get(n, {}).get("size_bytes")}
             for n in names]
    return ModelInfo(version=config.MODEL_VERSION, files=files)

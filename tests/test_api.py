import os
import tempfile

os.environ["DATABASE_URL"] = "sqlite:///" + os.path.join(tempfile.mkdtemp(), "t.db")
os.environ["ADMIN_KEY"] = "admin-test"

from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402

c = TestClient(app)
c.__enter__()  # startup 이벤트(테이블 생성)


def make_store(**kw):
    r = c.post("/api/stores", json={"name": "카페 손끝", "lat": 35.83, "lng": 128.75, **kw})
    assert r.status_code == 201, r.text
    return r.json()


def test_health_and_pages():
    assert c.get("/healthz").json()["ok"]
    assert c.get("/owner").status_code == 200
    assert c.get("/dashboard").status_code == 200
    assert c.get("/docs").status_code == 200


def test_store_and_menu_flow():
    s = make_store()
    code, key = s["code"], s["owner_key"]
    assert len(code) == 6 and s["menu_version"] == 0
    items = [
        {"category": "커피", "name": "아메리카노", "price": 4500, "aliases": ["아아", " 아아 ", ""],
         "options": [{"group": "온도", "values": ["HOT", "ICE"]}]},
        {"category": "커피", "name": "카페라떼", "price": 5000},
        {"category": "디저트", "name": "치즈케이크", "price": 6500, "sold_out": True},
    ]
    assert c.put(f"/api/stores/{code}/menu", json={"items": items}).status_code == 401
    assert c.put(f"/api/stores/{code}/menu", json={"items": items}, headers={"X-Owner-Key": "wrong"}).status_code == 401
    r = c.put(f"/api/stores/{code}/menu", json={"items": items}, headers={"X-Owner-Key": key})
    assert r.status_code == 200, r.text
    m = r.json()
    assert m["menu_version"] == 1 and m["categories"] == ["커피", "디저트"]
    assert m["items"][0]["aliases"] == ["아아"]
    # 공개 조회 + ETag
    r = c.get(f"/api/stores/{code.lower()}/menu")
    assert r.status_code == 200 and len(r.json()["items"]) == 3
    assert c.get(f"/api/stores/{code}/menu", headers={"If-None-Match": r.headers["etag"]}).status_code == 304
    # 다시 저장하면 버전이 오르고 이전 항목은 지워진다
    r = c.put(f"/api/stores/{code}/menu", json={"items": items[:1]}, headers={"X-Owner-Key": key})
    assert r.json()["menu_version"] == 2 and len(r.json()["items"]) == 1
    # 같은 이름 거부
    r = c.put(f"/api/stores/{code}/menu", json={"items": [items[1], items[1]]}, headers={"X-Owner-Key": key})
    assert r.status_code == 422
    # 운영자 키로도 수정 가능
    assert c.patch(f"/api/stores/{code}", json={"address": "경산시"}, headers={"X-Admin-Key": "admin-test"}).json()["address"] == "경산시"


def test_nearby():
    a = make_store(name="가까운 매장", lat=35.8300, lng=128.7500)
    make_store(name="먼 매장", lat=35.9000, lng=128.9000)
    make_store(name="위치 없음", lat=None, lng=None)
    r = c.get("/api/stores/nearby", params={"lat": 35.8301, "lng": 128.7501, "radius_m": 300}).json()
    assert a["code"] in [x["code"] for x in r] and all(x["distance_m"] <= 300 for x in r)
    assert "먼 매장" not in [x["name"] for x in r]


def test_stats():
    s = make_store()
    body = {"store_code": s["code"].lower(), "app_version": "0.1.0", "completed": True, "duration_s": 42.5,
            "steps": [{"screen_type": "menu", "target_kind": "tab", "result": "success", "reach_s": 3.2, "hints": 4},
                      {"screen_type": "menu", "target_kind": "menu", "result": "fail", "reach_s": 6.0,
                       "fail_reason": "no_change"},
                      {"screen_type": "option", "target_kind": "button", "result": "success", "reach_s": 2.0}]}
    assert c.post("/api/stats/sessions", json=body).status_code == 201
    assert c.post("/api/stats/sessions", json={**body, "completed": False, "steps": []}).status_code == 201
    # 잘못된 값은 거부
    bad = {**body, "steps": [{"result": "maybe"}]}
    assert c.post("/api/stats/sessions", json=bad).status_code == 422
    # 매장 통계: 점주 키
    assert c.get("/api/stats/summary", params={"store_code": s["code"]}).status_code == 401
    r = c.get("/api/stats/summary", params={"store_code": s["code"]}, headers={"X-Owner-Key": s["owner_key"]}).json()
    assert r["sessions"] == 2 and r["completed_rate"] == 0.5 and r["steps"] == 3
    assert r["by_screen"]["menu"]["success_rate"] == 0.5 and r["fail_reasons"] == {"no_change": 1}
    assert abs(r["avg_reach_s"] - 3.73) < 0.01
    # 전체 통계: 운영자 키
    assert c.get("/api/stats/summary").status_code == 401
    assert c.get("/api/stats/summary", headers={"X-Admin-Key": "admin-test"}).json()["sessions"] >= 2


def test_rate_limit():
    from app import config
    old = config.STATS_RATE_PER_MIN
    config.STATS_RATE_PER_MIN = 3
    from app.main import _rate
    _rate.clear()
    codes = [c.post("/api/stats/sessions", json={"completed": False, "duration_s": 1}).status_code for _ in range(5)]
    config.STATS_RATE_PER_MIN = old
    _rate.clear()
    assert codes[:3] == [201, 201, 201] and codes[3] == 429


def test_admin_list_and_models():
    assert c.get("/api/stores").status_code == 401
    assert isinstance(c.get("/api/stores", headers={"X-Admin-Key": "admin-test"}).json(), list)
    m = c.get("/api/models/latest").json()
    assert m["version"] and len(m["files"]) == 3


def test_delete():
    s = make_store()
    assert c.delete(f"/api/stores/{s['code']}", headers={"X-Owner-Key": s["owner_key"]}).status_code == 204
    assert c.get(f"/api/stores/{s['code']}").status_code == 404

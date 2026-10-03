"""Malformed input and geographic/proxy boundaries must not corrupt local data."""
from concurrent.futures import ThreadPoolExecutor

from .test_api import c, make_store


def test_invalid_store_and_menu_input_returns_validation_error():
    assert c.post("/api/stores", json={"name": "  "}).status_code == 422
    store = make_store()
    url = f"/api/stores/{store['code']}"
    headers = {"X-Owner-Key": store["owner_key"]}
    assert c.patch(url, json={"name": None}, headers=headers).status_code == 422
    assert c.patch(url, json={"name": "  "}, headers=headers).status_code == 422
    assert c.patch(url, json={"address": " updated "}, headers=headers).json()["address"] == "updated"
    assert c.get(url).json()["name"] == store["name"]
    assert c.put(url + "/menu", json={"items": [{"name": 42}]}, headers=headers).status_code == 422
    assert c.put(url + "/menu", json={"items": [{"name": "latte", "options": [{"group": " ", "values": ["large"]}]}]}, headers=headers).status_code == 422
    assert c.put(url + "/menu", json={"items": [{"name": "latte", "options": [{"group": "size", "values": ["x" * 81]}]}]}, headers=headers).status_code == 422
    assert c.get(url + "/menu").json()["menu_version"] == 0


def test_incomplete_store_location_is_ignored_by_nearby():
    store = make_store(name="longitude missing", lat=35.83, lng=None)
    result = c.get("/api/stores/nearby", params={"lat": 35.83, "lng": 128.75})
    assert result.status_code == 200
    assert store["code"] not in {row["code"] for row in result.json()}


def test_nearby_includes_high_latitude_and_dateline():
    northern = make_store(name="north", lat=80, lng=30.01)
    results = c.get("/api/stores/nearby", params={"lat": 80, "lng": 30, "radius_m": 300}).json()
    assert northern["code"] in {row["code"] for row in results}
    dateline = make_store(name="dateline", lat=0, lng=-179.999)
    results = c.get("/api/stores/nearby", params={"lat": 0, "lng": 179.999, "radius_m": 300}).json()
    assert dateline["code"] in {row["code"] for row in results}
    polar = make_store(name="polar", lat=89.999, lng=120)
    results = c.get("/api/stores/nearby", params={"lat": 90, "lng": 0, "radius_m": 300}).json()
    assert polar["code"] in {row["code"] for row in results}


def test_forwarded_header_cannot_reset_stats_quota(monkeypatch):
    from app import config, main
    monkeypatch.setattr(config, "STATS_RATE_PER_MIN", 2)
    main._rate.clear()
    try:
        statuses = [c.post("/api/stats/sessions", json={"completed": False, "duration_s": 1},
                           headers={"X-Forwarded-For": f"192.0.2.{i}"}) for i in range(3)]
        assert [r.status_code for r in statuses] == [201, 201, 429]
        assert 1 <= int(statuses[-1].headers["Retry-After"]) <= 60
    finally:
        main._rate.clear()


def test_simultaneous_requests_respect_shared_quota(monkeypatch):
    from app import config, main
    monkeypatch.setattr(config, "STATS_RATE_PER_MIN", 3)
    main._rate.clear()
    try:
        with ThreadPoolExecutor(max_workers=8) as pool:
            statuses = list(pool.map(lambda _: c.post("/api/stats/sessions", json={"completed": False, "duration_s": 1}).status_code, range(12)))
        assert statuses.count(201) == 3
        assert statuses.count(429) == 9
    finally:
        main._rate.clear()


def test_quota_and_client_tracking_expire(monkeypatch):
    from app import config, main
    monkeypatch.setattr(config, "STATS_RATE_PER_MIN", 1)
    monkeypatch.setattr(main, "_rate_last_cleanup", 0)
    moment = [1.0]
    monkeypatch.setattr(main.time, "monotonic", lambda: moment[0])
    main._rate.clear()
    try:
        assert c.post("/api/stats/sessions", json={"completed": False, "duration_s": 1}).status_code == 201
        assert c.post("/api/stats/sessions", json={"completed": False, "duration_s": 1}).status_code == 429
        main._rate["expired-client"].append(1.0)
        moment[0] = 61.0
        assert c.post("/api/stats/sessions", json={"completed": False, "duration_s": 1}).status_code == 201
        assert "expired-client" not in main._rate
    finally:
        main._rate.clear()

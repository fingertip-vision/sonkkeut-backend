from .test_api import c, make_store
from concurrent.futures import ThreadPoolExecutor


def test_offline_stats_retry_is_idempotent():
    store = make_store()
    payload = {"event_id": "session-test-001", "store_code": store["code"], "completed": True,
               "duration_s": 20, "steps": [{"screen_type": "menu", "target_kind": "menu", "result": "success"}]}
    assert c.post("/api/stats/sessions", json=payload).status_code == 201
    assert c.post("/api/stats/sessions", json=payload).json()["duplicate"] is True
    summary = c.get("/api/stats/summary", params={"store_code": store["code"]}, headers={"X-Owner-Key": store["owner_key"]}).json()
    assert summary["sessions"] == 1
    assert c.post("/api/stats/sessions", json={**payload, "completed": False}).status_code == 409


def test_private_fields_and_nonfinite_stats_rejected():
    payload = {"completed": False, "duration_s": 10}
    for field in ("image", "audio", "device_id", "location", "transcript"):
        assert c.post("/api/stats/sessions", json={**payload, field: "private"}).status_code == 422
    assert c.post("/api/stats/sessions", json={**payload, "steps": [{"result": "success", "finger": [0, 0]}]}).status_code == 422
    assert c.post("/api/stats/sessions", json={**payload, "duration_s": "NaN"}).status_code == 422


def test_blank_menu_name_rejected():
    store = make_store()
    assert c.put(f"/api/stores/{store['code']}/menu", json={"items": [{"name": "   "}]}, headers={"X-Owner-Key": store["owner_key"]}).status_code == 422


def test_kiosk_available():
    assert c.get("/kiosk").status_code == 200


def test_bundled_model_identity_is_available():
    files = c.get("/api/models/latest").json()["files"]
    assert len(files) == 3
    assert all(len(model["sha256"]) == 64 and model["size_bytes"] > 1_000_000 for model in files)


def test_simultaneous_offline_retries_count_once():
    store = make_store()
    payload = {"event_id": "parallel-session-001", "store_code": store["code"], "completed": True,
               "duration_s": 10, "steps": []}
    with ThreadPoolExecutor(max_workers=2) as workers:
        results = list(workers.map(lambda _: c.post("/api/stats/sessions", json=payload), range(2)))
    assert all(response.status_code == 201 for response in results)
    summary = c.get("/api/stats/summary", params={"store_code": store["code"]}, headers={"X-Owner-Key": store["owner_key"]}).json()
    assert summary["sessions"] == 1

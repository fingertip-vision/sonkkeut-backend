import os
import subprocess
import sys
from pathlib import Path

from .test_api import c, make_store


def production_process(script, **changes):
    env = {**os.environ, "APP_ENV": "production", "DATABASE_URL": "postgresql://dummy:dummy@localhost/unused",
           "ADMIN_KEY": "test-only-value-" + "x" * 40, "RENDER_EXTERNAL_HOSTNAME": "demo.onrender.com",
           "ALLOWED_HOSTS": "demo.onrender.com", "CORS_ORIGINS": ""}
    env.update(changes)
    return subprocess.run([sys.executable, "-c", script], env=env, cwd=Path(__file__).parents[1], capture_output=True, text=True, timeout=15)


def test_production_configuration_fails_closed_without_private_values():
    cases = [({"DATABASE_URL": "sqlite:///./private.db"}, "persistent PostgreSQL"),
             ({"ADMIN_KEY": "short-test-secret"}, "at least 32"),
             ({"ALLOWED_HOSTS": "*"}, "explicit ALLOWED_HOSTS"),
             ({"ALLOWED_HOSTS": "", "RENDER_EXTERNAL_HOSTNAME": ""}, "explicit ALLOWED_HOSTS"),
             ({"CORS_ORIGINS": "*"}, "HTTPS origins"),
             ({"CORS_ORIGINS": "http://demo.example"}, "HTTPS origins")]
    for changes, reason in cases:
        result = production_process("from app import config", **changes)
        assert result.returncode != 0 and reason in result.stderr
        assert "short-test-secret" not in result.stderr
        assert "dummy:dummy" not in result.stderr
    assert production_process("from app import config; assert config.PRODUCTION").returncode == 0


def test_production_public_entry_and_private_endpoint_boundaries():
    result = production_process("""
from fastapi.testclient import TestClient
from app.main import app
# No lifespan entry: PostgreSQL is deliberately not connected in this test.
c = TestClient(app, base_url='https://demo.onrender.com')
r = c.get('/', follow_redirects=False)
assert r.status_code == 307 and r.headers['location'] == '/simulation'
r = c.get('/simulation', headers={'Origin':'https://untrusted.example'})
assert r.status_code == 200 and 'access-control-allow-origin' not in r.headers
assert r.headers['strict-transport-security'] == 'max-age=31536000'
assert c.get('/simulation', headers={'Host':'untrusted.example'}).status_code == 400
for path in ('/api/stores', '/api/stats/summary'):
    r = c.get(path)
    assert r.status_code == 401 and r.headers['cache-control'] == 'no-store'
assert c.get('/static/local-admin.key').status_code == 404
assert c.get('/static/../.env').status_code == 404
""")
    assert result.returncode == 0, result.stderr


def test_owner_key_and_statistics_are_private_and_not_cached():
    store = make_store()
    body = c.get(f"/api/stores/{store['code']}").json()
    assert "owner_key" not in body and "owner_key_hash" not in body
    other = make_store()
    r = c.get("/api/stats/summary", params={"store_code": store["code"]}, headers={"X-Owner-Key": other["owner_key"]})
    assert r.status_code == 401 and r.headers["cache-control"] == "no-store"
    r = c.get("/api/stats/summary", params={"store_code": store["code"]}, headers={"X-Owner-Key": store["owner_key"]})
    assert r.status_code == 200 and r.headers["cache-control"] == "no-store"
    r = c.post("/api/stores", json={"name": "one-time owner key"})
    assert r.status_code == 201 and r.headers["cache-control"] == "no-store"
    assert r.headers["x-content-type-options"] == "nosniff"
    assert r.headers["x-frame-options"] == "DENY"


def test_non_ascii_admin_key_is_rejected_without_server_error():
    assert c.get("/api/stores", headers={b"X-Admin-Key": b"\xe9"}).status_code == 401


def test_large_json_and_chunked_requests_are_rejected_before_parsing(monkeypatch):
    from app import config
    monkeypatch.setattr(config, "MAX_REQUEST_BYTES", 1024)
    r = c.post("/api/stores", content=b"x" * 1025, headers={"Content-Type": "application/json"})
    assert r.status_code == 413
    r = c.post("/api/stores", content=iter([b"x" * 512] * 3), headers={"Content-Type": "application/json"})
    assert r.status_code == 413

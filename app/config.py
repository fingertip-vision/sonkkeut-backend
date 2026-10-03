"""
설정 — 모두 환경변수로 바꿀 수 있다 (Railway 등에서는 서비스 Variables에 넣는다)

  DATABASE_URL   Postgres 주소. 없으면 ./sonkkeut.db (SQLite)  예) postgresql://user:pw@host:5432/db
  ADMIN_KEY      운영자 키 (전체 통계, 매장 목록, 모델 버전 등록). 없으면 운영자 API를 막는다
  MODEL_VERSION  앱이 받을 최신 모델 버전 문자열 (선택)
  MODEL_BASE_URL 모델 파일을 내려받을 주소 (선택, 예: GitHub Release 주소)
  CORS_ORIGINS   웹 화면을 다른 도메인에서 부를 때 허용할 출처 (쉼표 구분, 기본 *)
"""
import os
from urllib.parse import urlsplit


def _db_url() -> str:
    url = os.getenv("DATABASE_URL", "sqlite:///./sonkkeut.db")
    # Railway·Heroku는 postgres:// 로 주기도 한다 → SQLAlchemy + psycopg3 형식으로 맞춘다
    if url.startswith("postgres://"):
        url = "postgresql://" + url[len("postgres://"):]
    if url.startswith("postgresql://"):
        url = "postgresql+psycopg://" + url[len("postgresql://"):]
    return url


APP_ENV = os.getenv("APP_ENV", "local").strip().lower()
PRODUCTION = APP_ENV == "production"
DATABASE_URL = _db_url()
ADMIN_KEY = os.getenv("ADMIN_KEY", "")
MODEL_VERSION = os.getenv("MODEL_VERSION", "2026.10.02")
MODEL_BASE_URL = os.getenv("MODEL_BASE_URL", "")
CORS_ORIGINS = [o.strip() for o in os.getenv("CORS_ORIGINS", "" if PRODUCTION else "*").split(",") if o.strip()]
ALLOWED_HOSTS = [h.strip() for h in os.getenv("ALLOWED_HOSTS", os.getenv("RENDER_EXTERNAL_HOSTNAME", "") if PRODUCTION else "*").split(",") if h.strip()]
MAX_REQUEST_BYTES = int(os.getenv("MAX_REQUEST_BYTES", "2097152"))

# 익명 통계 남용 방지: IP당 1분에 보낼 수 있는 세션 수
STATS_RATE_PER_MIN = int(os.getenv("STATS_RATE_PER_MIN", "30"))


def validate():
    """Fail closed before connecting to the DB; never include secret values in errors."""
    if STATS_RATE_PER_MIN < 1 or MAX_REQUEST_BYTES < 1024:
        raise RuntimeError("Request limits must be positive")
    if not PRODUCTION:
        return
    if not DATABASE_URL.startswith("postgresql+psycopg://"):
        raise RuntimeError("Production requires a persistent PostgreSQL DATABASE_URL")
    if len(ADMIN_KEY) < 32:
        raise RuntimeError("Production ADMIN_KEY must contain at least 32 characters")
    if not ALLOWED_HOSTS or "*" in ALLOWED_HOSTS:
        raise RuntimeError("Production requires explicit ALLOWED_HOSTS or RENDER_EXTERNAL_HOSTNAME")
    if any(urlsplit(origin).scheme != "https" or not urlsplit(origin).netloc for origin in CORS_ORIGINS):
        raise RuntimeError("Production CORS_ORIGINS must be explicit HTTPS origins")


validate()

"""
설정 — 모두 환경변수로 바꿀 수 있다 (Railway 등에서는 서비스 Variables에 넣는다)

  DATABASE_URL   Postgres 주소. 없으면 ./sonkkeut.db (SQLite)  예) postgresql://user:pw@host:5432/db
  ADMIN_KEY      운영자 키 (전체 통계, 매장 목록, 모델 버전 등록). 없으면 운영자 API를 막는다
  MODEL_VERSION  앱이 받을 최신 모델 버전 문자열 (선택)
  MODEL_BASE_URL 모델 파일을 내려받을 주소 (선택, 예: GitHub Release 주소)
  CORS_ORIGINS   웹 화면을 다른 도메인에서 부를 때 허용할 출처 (쉼표 구분, 기본 *)
"""
import os


def _db_url() -> str:
    url = os.getenv("DATABASE_URL", "sqlite:///./sonkkeut.db")
    # Railway·Heroku는 postgres:// 로 주기도 한다 → SQLAlchemy + psycopg3 형식으로 맞춘다
    if url.startswith("postgres://"):
        url = "postgresql://" + url[len("postgres://"):]
    if url.startswith("postgresql://"):
        url = "postgresql+psycopg://" + url[len("postgresql://"):]
    return url


DATABASE_URL = _db_url()
ADMIN_KEY = os.getenv("ADMIN_KEY", "")
MODEL_VERSION = os.getenv("MODEL_VERSION", "2026.10.02")
MODEL_BASE_URL = os.getenv("MODEL_BASE_URL", "")
CORS_ORIGINS = [o.strip() for o in os.getenv("CORS_ORIGINS", "*").split(",") if o.strip()]

# 익명 통계 남용 방지: IP당 1분에 보낼 수 있는 세션 수
STATS_RATE_PER_MIN = int(os.getenv("STATS_RATE_PER_MIN", "30"))

"""DB 테이블 (SQLAlchemy 2.0). SQLite와 Postgres 모두에서 동작한다."""
from datetime import datetime, timezone

from sqlalchemy import JSON, Boolean, DateTime, Float, ForeignKey, Integer, String, create_engine
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship, sessionmaker

from .config import DATABASE_URL

engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True,
    connect_args={"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {},
)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


def now():
    return datetime.now(timezone.utc)


class Base(DeclarativeBase):
    pass


class Store(Base):
    """매장 (점주가 등록). code는 키오스크 옆 스티커·QR에 적는 6자리 공개 코드"""

    __tablename__ = "stores"
    id: Mapped[int] = mapped_column(primary_key=True)
    code: Mapped[str] = mapped_column(String(12), unique=True, index=True)
    name: Mapped[str] = mapped_column(String(80))
    address: Mapped[str | None] = mapped_column(String(200), nullable=True)
    lat: Mapped[float | None] = mapped_column(Float, nullable=True)
    lng: Mapped[float | None] = mapped_column(Float, nullable=True)
    kiosk_vendor: Mapped[str | None] = mapped_column(String(80), nullable=True)
    owner_key_hash: Mapped[str] = mapped_column(String(128))
    menu_version: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now, onupdate=now)
    items: Mapped[list["MenuItem"]] = relationship(back_populates="store", cascade="all, delete-orphan",
                                                   order_by="MenuItem.sort")


class MenuItem(Base):
    """메뉴 한 개. aliases는 OCR·음성 인식이 틀리기 쉬운 다른 이름 (예: 아아 → 아이스 아메리카노)"""

    __tablename__ = "menu_items"
    id: Mapped[int] = mapped_column(primary_key=True)
    store_id: Mapped[int] = mapped_column(ForeignKey("stores.id", ondelete="CASCADE"), index=True)
    category: Mapped[str] = mapped_column(String(40), default="")
    name: Mapped[str] = mapped_column(String(80))
    price: Mapped[int | None] = mapped_column(Integer, nullable=True)
    aliases: Mapped[list] = mapped_column(JSON, default=list)
    options: Mapped[list] = mapped_column(JSON, default=list)  # [{"group":"온도","values":["HOT","ICE"]}]
    sold_out: Mapped[bool] = mapped_column(Boolean, default=False)
    sort: Mapped[int] = mapped_column(Integer, default=0)
    store: Mapped[Store] = relationship(back_populates="items")


class UsageSession(Base):
    """익명 사용 기록 한 번 (F-15). 영상·음성·위치·기기 식별자는 저장하지 않는다"""

    __tablename__ = "usage_sessions"
    id: Mapped[int] = mapped_column(primary_key=True)
    store_code: Mapped[str | None] = mapped_column(String(12), index=True, nullable=True)
    app_version: Mapped[str] = mapped_column(String(20), default="")
    model_version: Mapped[str] = mapped_column(String(20), default="")
    completed: Mapped[bool] = mapped_column(Boolean, default=False)
    duration_s: Mapped[float] = mapped_column(Float, default=0.0)
    n_steps: Mapped[int] = mapped_column(Integer, default=0)
    steps: Mapped[list] = mapped_column(JSON, default=list)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now, index=True)


def init_db():
    Base.metadata.create_all(engine)

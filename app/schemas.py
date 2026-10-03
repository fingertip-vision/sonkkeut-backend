"""요청·응답 형식 (앱과 점주 웹이 주고받는 JSON)"""
from datetime import datetime
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, field_validator


class OptionGroup(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    group: str = Field(min_length=1, max_length=30)
    values: list[Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=80)]] = Field(default_factory=list, max_length=20)


class MenuItemIn(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    category: str = Field("", max_length=40)
    name: str = Field(min_length=1, max_length=80)
    price: int | None = Field(None, ge=0, le=10_000_000)
    aliases: list[str] = Field(default_factory=list, max_length=20)
    options: list[OptionGroup] = Field(default_factory=list, max_length=10)
    sold_out: bool = False

    @field_validator("aliases")
    @classmethod
    def clean_aliases(cls, v: list[str]) -> list[str]:
        out = []
        for a in v:
            a = a.strip()
            if a and a not in out and len(a) <= 80:
                out.append(a)
        return out


class MenuItemOut(MenuItemIn):
    id: int


class StoreCreate(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True, allow_inf_nan=False)
    name: str = Field(min_length=1, max_length=80)
    address: str | None = Field(None, max_length=200)
    lat: float | None = Field(None, ge=-90, le=90)
    lng: float | None = Field(None, ge=-180, le=180)
    kiosk_vendor: str | None = Field(None, max_length=80)


class StoreUpdate(StoreCreate):
    name: str | None = Field(None, min_length=1, max_length=80)

    @field_validator("name")
    @classmethod
    def name_cannot_be_cleared(cls, value):
        # Omission is allowed in PATCH; an explicit null cannot fit the DB column.
        if value is None:
            raise ValueError("매장 이름은 비울 수 없습니다")
        return value


class StoreOut(BaseModel):
    code: str
    name: str
    address: str | None
    lat: float | None
    lng: float | None
    kiosk_vendor: str | None
    menu_version: int
    updated_at: datetime


class StoreCreated(StoreOut):
    owner_key: str = Field(description="점주 키. 이 응답에서 한 번만 보여 준다. 메뉴 수정에 필요")


class NearbyStore(StoreOut):
    distance_m: float


class MenuOut(BaseModel):
    """앱이 받는 메뉴 사전 (F-04 문자 인식 보정, F-06 음성 주문 이해에 쓴다)"""

    store_code: str
    store_name: str
    menu_version: int
    categories: list[str]
    items: list[MenuItemOut]


class MenuReplace(BaseModel):
    model_config = ConfigDict(extra="forbid")
    items: list[MenuItemIn] = Field(max_length=500)


# ---------- 익명 통계 (F-15) ----------
ScreenType = Literal["menu", "option", "cart", "payment", "start", "unknown"]


class StepIn(BaseModel):
    """누르기 한 번의 기록. 영상·음성·좌표는 보내지 않는다"""
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

    screen_type: ScreenType = "unknown"
    target_kind: Literal["tab", "menu", "price", "button", "back", "unknown"] = "unknown"
    result: Literal["success", "fail", "uncertain", "restarted", "abandoned"]
    reach_s: float | None = Field(None, ge=0, le=600, description="목표 지정 → '지금 누르세요'까지 초")
    hints: int = Field(0, ge=0, le=1000, description="이 단계에서 나간 안내 음성 수")
    fail_reason: str | None = Field(None, max_length=60, description="no_change, unexpected:screen_type 등")


class SessionIn(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    event_id: str | None = Field(None, min_length=12, max_length=80, pattern=r"^[A-Za-z0-9-]+$")
    store_code: str | None = Field(None, max_length=12)
    app_version: str = Field("", max_length=20)
    model_version: str = Field("", max_length=20)
    completed: bool = Field(description="결제 화면까지 갔는가")
    duration_s: float = Field(ge=0, le=7200)
    steps: list[StepIn] = Field(default_factory=list, max_length=200)


class Summary(BaseModel):
    sessions: int
    completed_rate: float | None
    steps: int
    step_success_rate: float | None
    avg_reach_s: float | None
    avg_duration_s: float | None
    by_screen: dict[str, dict]
    fail_reasons: dict[str, int]
    daily: list[dict]


class ModelInfo(BaseModel):
    version: str
    files: list[dict]

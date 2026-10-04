package com.sonkkeut.backend.menu;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 점주 화면의 '저장'. 표 전체를 한 번에 보내고 서버는 기존 메뉴를 통째로 바꾼다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MenuReplace(@NotNull @Size(max = 500) List<@NotNull @Valid MenuItemIn> items) {
}

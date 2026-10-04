package com.sonkkeut.backend.menu;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 메뉴 옵션 한 묶음. 예: {"group": "온도", "values": ["HOT", "ICE"]} */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OptionGroup(@NotNull @Size(max = 30) String group, @Size(max = 20) List<@NotNull String> values) {

	public OptionGroup {
		values = values == null ? List.of() : values;
	}
}

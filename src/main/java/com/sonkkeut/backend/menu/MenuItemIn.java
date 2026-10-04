package com.sonkkeut.backend.menu;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 점주가 저장하는 메뉴 한 개. 엑셀에서 붙여 넣은 값에 공백이 섞이기 쉬워서, 검사 전에 앞뒤 공백을 지우고
 * 비었거나 겹치는 다른 이름은 버린다. 그래서 공백뿐인 메뉴 이름은 빈 이름으로 거부된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MenuItemIn(
		@Size(max = 40) String category,
		@NotNull @Size(min = 1, max = 80) String name,
		@Min(0) @Max(10_000_000) Integer price,
		@Size(max = 20) List<String> aliases,
		@Size(max = 10) List<@NotNull @Valid OptionGroup> options,
		Boolean soldOut) {

	private static final int ALIAS_MAX_LENGTH = 80;

	public MenuItemIn {
		category = category == null ? "" : category.strip();
		name = name == null ? null : name.strip();
		aliases = cleanAliases(aliases);
		options = options == null ? List.of() : options;
		soldOut = soldOut != null && soldOut;
	}

	private static List<String> cleanAliases(List<String> raw) {
		List<String> out = new ArrayList<>();
		if (raw == null) {
			return out;
		}
		for (String alias : raw) {
			String a = alias == null ? "" : alias.strip();
			if (!a.isEmpty() && !out.contains(a) && a.length() <= ALIAS_MAX_LENGTH) {
				out.add(a);
			}
		}
		return out;
	}
}

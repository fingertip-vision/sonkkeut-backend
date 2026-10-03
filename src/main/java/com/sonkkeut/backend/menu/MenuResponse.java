package com.sonkkeut.backend.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MenuResponse(Long id, Long categoryId, String name, int price, boolean soldOut, int sortOrder,
		List<String> aliases, Map<String, List<String>> options) {

	static MenuResponse from(Menu menu) {
		return new MenuResponse(menu.getId(), menu.getCategory().getId(), menu.getName(), menu.getPrice(),
				menu.isSoldOut(), menu.getSortOrder(), List.copyOf(menu.getAliases()), groupOptions(menu));
	}

	static Map<String, List<String>> groupOptions(Menu menu) {
		Map<String, List<String>> grouped = new LinkedHashMap<>();
		for (MenuOption option : menu.getOptions()) {
			grouped.computeIfAbsent(option.kind(), k -> new ArrayList<>()).add(option.value());
		}
		return grouped;
	}
}

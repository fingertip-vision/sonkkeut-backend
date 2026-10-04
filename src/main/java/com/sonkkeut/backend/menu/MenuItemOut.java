package com.sonkkeut.backend.menu;

import java.util.List;

public record MenuItemOut(Long id, String category, String name, Integer price, List<String> aliases,
		List<OptionGroup> options, boolean soldOut) {

	static MenuItemOut from(MenuItem item) {
		return new MenuItemOut(item.getId(), item.getCategory(), item.getName(), item.getPrice(), item.getAliases(),
				item.getOptions(), item.isSoldOut());
	}
}

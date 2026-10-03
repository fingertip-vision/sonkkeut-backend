package com.sonkkeut.backend.menu;

public record CategoryResponse(Long id, String name, int sortOrder) {

	static CategoryResponse from(MenuCategory category) {
		return new CategoryResponse(category.getId(), category.getName(), category.getSortOrder());
	}
}

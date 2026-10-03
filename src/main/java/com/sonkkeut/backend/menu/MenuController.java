package com.sonkkeut.backend.menu;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** F-14 매장 메뉴 등록. 점주 화면에서 쓰는 카테고리·메뉴 등록·수정·삭제. */
@RestController
public class MenuController {

	private final MenuService menuService;

	public MenuController(MenuService menuService) {
		this.menuService = menuService;
	}

	@GetMapping("/api/v1/stores/{storeId}/categories")
	public List<CategoryResponse> listCategories(@PathVariable Long storeId) {
		return menuService.listCategories(storeId);
	}

	@PostMapping("/api/v1/stores/{storeId}/categories")
	@ResponseStatus(HttpStatus.CREATED)
	public CategoryResponse createCategory(@PathVariable Long storeId, @Valid @RequestBody CategoryRequest request) {
		return menuService.createCategory(storeId, request);
	}

	@PutMapping("/api/v1/stores/{storeId}/categories/{categoryId}")
	public CategoryResponse updateCategory(@PathVariable Long storeId, @PathVariable Long categoryId,
			@Valid @RequestBody CategoryRequest request) {
		return menuService.updateCategory(storeId, categoryId, request);
	}

	@DeleteMapping("/api/v1/stores/{storeId}/categories/{categoryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteCategory(@PathVariable Long storeId, @PathVariable Long categoryId) {
		menuService.deleteCategory(storeId, categoryId);
	}

	@GetMapping("/api/v1/stores/{storeId}/menus")
	public List<MenuResponse> listMenus(@PathVariable Long storeId) {
		return menuService.listMenus(storeId);
	}

	@PostMapping("/api/v1/stores/{storeId}/menus")
	@ResponseStatus(HttpStatus.CREATED)
	public MenuResponse createMenu(@PathVariable Long storeId, @Valid @RequestBody MenuRequest request) {
		return menuService.createMenu(storeId, request);
	}

	@PutMapping("/api/v1/stores/{storeId}/menus/{menuId}")
	public MenuResponse updateMenu(@PathVariable Long storeId, @PathVariable Long menuId,
			@Valid @RequestBody MenuRequest request) {
		return menuService.updateMenu(storeId, menuId, request);
	}

	@DeleteMapping("/api/v1/stores/{storeId}/menus/{menuId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMenu(@PathVariable Long storeId, @PathVariable Long menuId) {
		menuService.deleteMenu(storeId, menuId);
	}
}

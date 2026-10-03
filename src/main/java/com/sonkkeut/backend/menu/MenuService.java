package com.sonkkeut.backend.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonkkeut.backend.common.BadRequestException;
import com.sonkkeut.backend.common.NotFoundException;
import com.sonkkeut.backend.store.Store;
import com.sonkkeut.backend.store.StoreRepository;
import com.sonkkeut.backend.store.StoreService;

@Service
public class MenuService {

	private final StoreService storeService;
	private final StoreRepository storeRepository;
	private final MenuCategoryRepository categoryRepository;
	private final MenuRepository menuRepository;

	public MenuService(StoreService storeService, StoreRepository storeRepository,
			MenuCategoryRepository categoryRepository, MenuRepository menuRepository) {
		this.storeService = storeService;
		this.storeRepository = storeRepository;
		this.categoryRepository = categoryRepository;
		this.menuRepository = menuRepository;
	}

	@Transactional
	public CategoryResponse createCategory(Long storeId, CategoryRequest request) {
		Store store = storeService.find(storeId);
		MenuCategory category = categoryRepository.save(new MenuCategory(store, request.name(), request.sortOrder()));
		store.touchDictionary();
		return CategoryResponse.from(category);
	}

	@Transactional
	public CategoryResponse updateCategory(Long storeId, Long categoryId, CategoryRequest request) {
		MenuCategory category = findCategory(storeId, categoryId);
		category.update(request.name(), request.sortOrder());
		category.getStore().touchDictionary();
		return CategoryResponse.from(category);
	}

	@Transactional
	public void deleteCategory(Long storeId, Long categoryId) {
		MenuCategory category = findCategory(storeId, categoryId);
		// 메뉴가 통째로 사라지는 실수를 막으려고, 비어 있는 카테고리만 지운다.
		if (menuRepository.existsByCategory(category)) {
			throw new BadRequestException("category not empty");
		}
		categoryRepository.delete(category);
		category.getStore().touchDictionary();
	}

	@Transactional(readOnly = true)
	public List<CategoryResponse> listCategories(Long storeId) {
		Store store = storeService.find(storeId);
		return categoryRepository.findByStoreOrderBySortOrderAscIdAsc(store).stream()
				.map(CategoryResponse::from).toList();
	}

	@Transactional
	public MenuResponse createMenu(Long storeId, MenuRequest request) {
		MenuCategory category = findCategory(storeId, request.categoryId());
		Menu menu = menuRepository.save(new Menu(category, request.name(), request.price(), request.soldOut(),
				request.sortOrder(), request.aliases(), optionsOf(request)));
		category.getStore().touchDictionary();
		return MenuResponse.from(menu);
	}

	@Transactional
	public MenuResponse updateMenu(Long storeId, Long menuId, MenuRequest request) {
		Menu menu = findMenu(storeId, menuId);
		MenuCategory category = findCategory(storeId, request.categoryId());
		menu.update(category, request.name(), request.price(), request.soldOut(), request.sortOrder(),
				request.aliases(), optionsOf(request));
		category.getStore().touchDictionary();
		return MenuResponse.from(menu);
	}

	@Transactional
	public void deleteMenu(Long storeId, Long menuId) {
		Menu menu = findMenu(storeId, menuId);
		menuRepository.delete(menu);
		menu.getCategory().getStore().touchDictionary();
	}

	@Transactional(readOnly = true)
	public List<MenuResponse> listMenus(Long storeId) {
		Store store = storeService.find(storeId);
		return menuRepository.findByCategoryStoreOrderBySortOrderAscIdAsc(store).stream()
				.map(MenuResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public DictionaryResponse dictionary(String storeCode) {
		Store store = storeRepository.findByStoreCode(storeCode)
				.orElseThrow(() -> new NotFoundException("store not found"));
		List<Menu> menus = menuRepository.findByCategoryStoreOrderBySortOrderAscIdAsc(store);
		List<DictionaryResponse.Category> categories = categoryRepository.findByStoreOrderBySortOrderAscIdAsc(store)
				.stream()
				.map(category -> new DictionaryResponse.Category(category.getName(), menus.stream()
						.filter(menu -> menu.getCategory().getId().equals(category.getId()))
						.map(menu -> new DictionaryResponse.Item(menu.getName(), menu.getPrice(), menu.isSoldOut(),
								List.copyOf(menu.getAliases()), MenuResponse.groupOptions(menu)))
						.toList()))
				.toList();
		return new DictionaryResponse(store.getName(), store.getDictionaryVersion(), categories);
	}

	// 다른 매장의 카테고리·메뉴는 없는 것으로 취급해, ID를 추측해도 존재 여부를 알 수 없게 한다.
	private MenuCategory findCategory(Long storeId, Long categoryId) {
		return categoryRepository.findById(categoryId)
				.filter(category -> category.getStore().getId().equals(storeId))
				.orElseThrow(() -> new NotFoundException("category not found"));
	}

	private Menu findMenu(Long storeId, Long menuId) {
		return menuRepository.findById(menuId)
				.filter(menu -> menu.getCategory().getStore().getId().equals(storeId))
				.orElseThrow(() -> new NotFoundException("menu not found"));
	}

	private static List<MenuOption> optionsOf(MenuRequest request) {
		List<MenuOption> options = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : request.options().entrySet()) {
			for (String value : entry.getValue()) {
				options.add(new MenuOption(entry.getKey(), value));
			}
		}
		return options;
	}
}

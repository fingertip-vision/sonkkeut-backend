package com.sonkkeut.backend.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonkkeut.backend.common.ApiException;
import com.sonkkeut.backend.store.Store;
import com.sonkkeut.backend.store.StoreAccess;
import com.sonkkeut.backend.store.StoreRepository;
import com.sonkkeut.backend.store.StoreService;

@Service
public class MenuService {

	private final StoreService storeService;
	private final StoreAccess storeAccess;
	private final StoreRepository storeRepository;

	public MenuService(StoreService storeService, StoreAccess storeAccess, StoreRepository storeRepository) {
		this.storeService = storeService;
		this.storeAccess = storeAccess;
		this.storeRepository = storeRepository;
	}

	@Transactional(readOnly = true)
	public MenuOut get(String code) {
		return toOut(storeService.find(code));
	}

	@Transactional
	public MenuOut replace(String code, MenuReplace body, String ownerKey, String adminKey) {
		Store store = storeService.find(code);
		storeAccess.requireOwner(store, ownerKey, adminKey);
		// 앱은 메뉴 이름으로 화면 글자를 맞춰 보므로, 같은 이름이 둘이면 어느 쪽인지 정할 수 없다.
		List<String> duplicates = duplicateNames(body.items());
		if (!duplicates.isEmpty()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
					"같은 이름의 메뉴가 있습니다: " + String.join(", ", duplicates.subList(0, Math.min(5, duplicates.size()))));
		}
		List<MenuItem> items = new ArrayList<>();
		for (int i = 0; i < body.items().size(); i++) {
			items.add(new MenuItem(store, body.items().get(i), i));
		}
		store.replaceItems(items);
		// 응답에 새 메뉴 ID가 들어가야 하므로 먼저 DB에 반영한다.
		storeRepository.flush();
		return toOut(store);
	}

	private static MenuOut toOut(Store store) {
		List<MenuItemOut> items = store.getItems().stream().map(MenuItemOut::from).toList();
		List<String> categories = items.stream().map(MenuItemOut::category).filter(c -> !c.isEmpty()).distinct()
				.toList();
		return new MenuOut(store.getCode(), store.getName(), store.getMenuVersion(), categories, items);
	}

	private static List<String> duplicateNames(List<MenuItemIn> items) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		items.forEach(item -> counts.merge(item.name(), 1, Integer::sum));
		return counts.entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).toList();
	}
}

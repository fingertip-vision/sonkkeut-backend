package com.sonkkeut.backend.menu;

import java.util.List;
import java.util.Map;

/** 앱이 내려받아 캐시하는 매장 메뉴 사전. F-04 메뉴명 보정과 F-06 단어 보정에 쓰인다. */
public record DictionaryResponse(String storeName, int version, List<Category> categories) {

	public record Category(String name, List<Item> menus) {
	}

	public record Item(String name, int price, boolean soldOut, List<String> aliases,
			Map<String, List<String>> options) {
	}
}

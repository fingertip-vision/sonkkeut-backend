package com.sonkkeut.backend.menu;

import java.util.List;

/** 앱이 받는 메뉴 사전. F-04 문자 인식 보정과 F-06 음성 주문 이해에 쓰인다. */
public record MenuOut(String storeCode, String storeName, int menuVersion, List<String> categories,
		List<MenuItemOut> items) {
}

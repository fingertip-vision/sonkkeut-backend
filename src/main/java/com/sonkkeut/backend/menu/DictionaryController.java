package com.sonkkeut.backend.menu;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 앱이 매장 코드로 메뉴 사전을 받아 간다. 주문 중에는 캐시만 쓰므로 이 API가 실패해도 주문은 계속된다. */
@RestController
public class DictionaryController {

	private final MenuService menuService;

	public DictionaryController(MenuService menuService) {
		this.menuService = menuService;
	}

	@GetMapping("/api/v1/dictionaries/{storeCode}")
	public ResponseEntity<DictionaryResponse> get(@PathVariable String storeCode,
			@RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
		DictionaryResponse dictionary = menuService.dictionary(storeCode);
		String etag = "\"" + dictionary.version() + "\"";
		// 앱이 가진 버전과 같으면 본문을 다시 보내지 않아 데이터 사용을 줄인다.
		if (etag.equals(ifNoneMatch)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
		}
		return ResponseEntity.ok().eTag(etag).body(dictionary);
	}
}

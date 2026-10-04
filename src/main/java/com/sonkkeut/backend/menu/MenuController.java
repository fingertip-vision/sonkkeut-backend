package com.sonkkeut.backend.menu;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** F-14 메뉴. 조회는 앱이 인증 없이, 저장은 점주 키나 운영자 키로 한다. */
@RestController
public class MenuController {

	private final MenuService menuService;

	public MenuController(MenuService menuService) {
		this.menuService = menuService;
	}

	@GetMapping("/api/stores/{code}/menu")
	public ResponseEntity<MenuOut> get(@PathVariable String code,
			@RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch) {
		MenuOut menu = menuService.get(code);
		String etag = "\"" + menu.storeCode() + "-" + menu.menuVersion() + "\"";
		// 앱이 가진 버전과 같으면 본문을 다시 보내지 않아 데이터 사용을 줄인다.
		if (etag.equals(ifNoneMatch)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
		}
		return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache()).body(menu);
	}

	@PutMapping("/api/stores/{code}/menu")
	public MenuOut replace(@PathVariable String code, @Valid @RequestBody MenuReplace body,
			@RequestHeader(name = "X-Owner-Key", required = false) String ownerKey,
			@RequestHeader(name = "X-Admin-Key", required = false) String adminKey) {
		return menuService.replace(code, body, ownerKey, adminKey);
	}
}

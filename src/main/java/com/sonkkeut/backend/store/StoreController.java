package com.sonkkeut.backend.store;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sonkkeut.backend.common.ApiException;
import com.sonkkeut.backend.common.ClientIp;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.JsonNode;

/** F-14 매장. 조회·근처 매장·등록은 누구나, 수정·삭제는 점주 키나 운영자 키가 있어야 한다. */
@RestController
public class StoreController {

	private final StoreService storeService;
	private final StoreCreateRateLimiter createRateLimiter;

	public StoreController(StoreService storeService, StoreCreateRateLimiter createRateLimiter) {
		this.storeService = storeService;
		this.createRateLimiter = createRateLimiter;
	}

	@PostMapping("/api/stores")
	@ResponseStatus(HttpStatus.CREATED)
	public StoreCreated create(@Valid @RequestBody StoreCreate body, HttpServletRequest request,
			HttpServletResponse response) {
		createRateLimiter.check(ClientIp.of(request));
		response.setHeader("Cache-Control", "no-store");
		return storeService.create(body);
	}

	@GetMapping("/api/stores/nearby")
	public List<NearbyStore> nearby(@RequestParam double lat, @RequestParam double lng,
			@RequestParam(name = "radius_m", defaultValue = "300") double radiusM) {
		if (Math.abs(lat) > 90 || Math.abs(lng) > 180 || !(radiusM > 0 && radiusM <= 5000)) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "lat·lng 범위 또는 radius_m(0 초과 5000 이하)이 맞지 않습니다");
		}
		return storeService.nearby(lat, lng, radiusM);
	}

	@GetMapping("/api/stores")
	public List<StoreOut> list(@RequestHeader(name = "X-Admin-Key", required = false) String adminKey) {
		return storeService.list(adminKey);
	}

	@GetMapping("/api/stores/{code}")
	public StoreOut get(@PathVariable String code) {
		return storeService.get(code);
	}

	@PatchMapping("/api/stores/{code}")
	public StoreOut update(@PathVariable String code, @RequestBody JsonNode body,
			@RequestHeader(name = "X-Owner-Key", required = false) String ownerKey,
			@RequestHeader(name = "X-Admin-Key", required = false) String adminKey) {
		return storeService.update(code, body, ownerKey, adminKey);
	}

	@DeleteMapping("/api/stores/{code}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String code,
			@RequestHeader(name = "X-Owner-Key", required = false) String ownerKey,
			@RequestHeader(name = "X-Admin-Key", required = false) String adminKey) {
		storeService.delete(code, ownerKey, adminKey);
	}
}

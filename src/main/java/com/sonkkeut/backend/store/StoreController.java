package com.sonkkeut.backend.store;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** F-14 매장 등록. 점주 인증은 계정 필요 여부가 정해지면 붙인다. */
@RestController
public class StoreController {

	private final StoreService storeService;

	public StoreController(StoreService storeService) {
		this.storeService = storeService;
	}

	@PostMapping("/api/v1/stores")
	@ResponseStatus(HttpStatus.CREATED)
	public StoreResponse create(@Valid @RequestBody StoreRequest request) {
		return storeService.create(request);
	}

	@GetMapping("/api/v1/stores/{storeId}")
	public StoreResponse get(@PathVariable Long storeId) {
		return storeService.get(storeId);
	}
}

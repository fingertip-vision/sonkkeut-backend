package com.sonkkeut.backend.menu;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** options는 주문 의도 JSON과 같은 키로 받는다. 예: {"temp": ["hot", "ice"]} */
public record MenuRequest(
		@NotNull Long categoryId,
		@NotBlank @Size(max = 100) String name,
		@NotNull @PositiveOrZero Integer price,
		Boolean soldOut,
		Integer sortOrder,
		List<@NotBlank @Size(max = 100) String> aliases,
		Map<@NotBlank @Size(max = 20) String, List<@NotBlank @Size(max = 20) String>> options) {

	// 선택 항목은 생략할 수 있게 기본값을 채운다. Jackson 3은 원시 타입 필드가 빠지면 요청을 거부한다.
	public MenuRequest {
		soldOut = soldOut != null && soldOut;
		sortOrder = sortOrder == null ? 0 : sortOrder;
		aliases = aliases == null ? List.of() : aliases;
		options = options == null ? Map.of() : options;
	}
}

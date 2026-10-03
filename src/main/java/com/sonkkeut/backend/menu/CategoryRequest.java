package com.sonkkeut.backend.menu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(@NotBlank @Size(max = 50) String name, Integer sortOrder) {

	// 순서는 생략할 수 있게 기본값을 채운다. Jackson 3은 원시 타입 필드가 빠지면 요청을 거부한다.
	public CategoryRequest {
		sortOrder = sortOrder == null ? 0 : sortOrder;
	}
}

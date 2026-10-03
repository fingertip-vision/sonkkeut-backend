package com.sonkkeut.backend.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** 메뉴가 고를 수 있는 옵션 값 하나. kind는 주문 의도 JSON의 options 키(temp 등)와 같은 이름을 쓴다. */
@Embeddable
public record MenuOption(
		@Column(name = "kind", nullable = false, length = 20) String kind,
		@Column(name = "option_value", nullable = false, length = 20) String value) {
}

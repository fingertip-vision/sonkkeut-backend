package com.sonkkeut.backend.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** 주문 중 발생한 예외 한 건. 어느 상태에서 무엇이 일어났는지만 남긴다. */
@Embeddable
public record UsageError(
		@Enumerated(EnumType.STRING) @Column(name = "state", nullable = false, length = 10) AppState state,
		@Enumerated(EnumType.STRING) @Column(name = "kind", nullable = false, length = 30) ErrorKind kind,
		@Column(name = "elapsed_ms", nullable = false) long elapsedMs) {
}

package com.sonkkeut.backend.stats;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 앱이 주문이 끝난 뒤 한 번에 보내는 익명 기록. storeCode는 매장을 모르면 생략한다. */
public record UsageSessionRequest(
		@NotNull @Pattern(regexp = "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}") String sessionId,
		@Size(max = 6) String storeCode,
		@NotBlank @Size(max = 20) String appVersion,
		@NotNull SessionResult result,
		@NotNull AppState endState,
		@NotNull @PositiveOrZero Long durationMs,
		@Size(max = 100) List<@Valid @NotNull Error> errors) {

	public UsageSessionRequest {
		errors = errors == null ? List.of() : errors;
	}

	public record Error(@NotNull AppState state, @NotNull ErrorKind kind, @NotNull @PositiveOrZero Long elapsedMs) {
	}
}

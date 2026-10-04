package com.sonkkeut.backend.stats;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 앱이 주문 한 번을 마치거나 그만둘 때 보내는 익명 기록(F-15).
 * event_id는 주문마다 새로 만드는 값이라 기기를 가리키지 않고, 재전송을 한 번만 세는 데만 쓴다.
 */
public record SessionIn(
		@Size(min = 12, max = 80) @Pattern(regexp = "^[A-Za-z0-9-]+$") String eventId,
		@Size(max = 12) String storeCode,
		@Size(max = 20) String appVersion,
		@Size(max = 20) String modelVersion,
		// 결제 화면까지 갔는가
		@NotNull Boolean completed,
		@NotNull @DecimalMin("0") @DecimalMax("7200") Double durationS,
		@Size(max = 200) List<@NotNull @Valid StepIn> steps) {

	public SessionIn {
		appVersion = appVersion == null ? "" : appVersion;
		modelVersion = modelVersion == null ? "" : modelVersion;
		steps = steps == null ? List.of() : steps;
	}

	@JsonIgnore
	@AssertTrue(message = "duration_s는 유한한 숫자여야 합니다")
	public boolean isDurationFinite() {
		return durationS == null || Double.isFinite(durationS);
	}

	/** 같은 event_id로 다른 내용이 오는지 비교할 때 쓰는, event_id를 뺀 내용. */
	SessionIn withoutEventId() {
		return new SessionIn(null, storeCode, appVersion, modelVersion, completed, durationS, steps);
	}
}

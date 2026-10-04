package com.sonkkeut.backend.stats;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 누르기 한 번의 기록(F-10 판정). 영상·음성·좌표는 받지 않는다.
 * 정의되지 않은 필드는 전역 설정(fail-on-unknown-properties)으로 422가 된다.
 */
public record StepIn(
		@Pattern(regexp = "menu|option|cart|method|payment|start|unknown") String screenType,
		@Pattern(regexp = "tab|menu|price|button|back|unknown") String targetKind,
		@NotNull @Pattern(regexp = "success|fail|uncertain|restarted|abandoned") String result,
		// 목표 지정부터 "지금 누르세요"까지 걸린 초
		@DecimalMin("0") @DecimalMax("600") Double reachS,
		// 이 단계에서 나간 안내 음성 수
		@Min(0) @Max(1000) Integer hints,
		// no_change, unexpected:screen_type 등
		@Size(max = 60) String failReason) {

	public StepIn {
		screenType = screenType == null ? "unknown" : screenType;
		targetKind = targetKind == null ? "unknown" : targetKind;
		hints = hints == null ? 0 : hints;
	}

	// "NaN" 같은 문자열도 숫자로 읽히므로 범위 검사와 따로 막는다.
	@JsonIgnore
	@AssertTrue(message = "유한한 숫자여야 합니다")
	public boolean isReachFinite() {
		return reachS == null || Double.isFinite(reachS);
	}
}

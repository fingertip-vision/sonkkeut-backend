package com.sonkkeut.backend.store;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 매장 등록 요청. 점주 화면이 모르는 필드를 함께 보내도 막지 않는다(통계와 달리 개인정보가 섞일 일이 없다). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoreCreate(
		@NotNull @Size(min = 1, max = 80) String name,
		@Size(max = 200) String address,
		@DecimalMin("-90") @DecimalMax("90") Double lat,
		@DecimalMin("-180") @DecimalMax("180") Double lng,
		@Size(max = 80) String kioskVendor) {
}

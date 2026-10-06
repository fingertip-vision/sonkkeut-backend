package com.sonkkeut.backend.store;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sonkkeut.backend.common.IpRateLimiter;

/** 매장 등록 한도. 등록은 인증 없이 열려 있어서, 매장을 대량으로 만들어 목록·근처 매장을 채우지 못하게 막는다. */
@Component
public class StoreCreateRateLimiter extends IpRateLimiter {

	public StoreCreateRateLimiter(@Value("${store.create-rate-per-min}") int limitPerMinute) {
		super(limitPerMinute, "잠시 후 다시 등록해 주세요");
	}
}

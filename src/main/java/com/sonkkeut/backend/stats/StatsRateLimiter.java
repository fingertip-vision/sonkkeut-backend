package com.sonkkeut.backend.stats;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sonkkeut.backend.common.IpRateLimiter;

/** 통계 전송 한도. 앱은 주문 한 번에 한 건만 보내므로 넉넉히 둔다. */
@Component
public class StatsRateLimiter extends IpRateLimiter {

	public StatsRateLimiter(@Value("${stats.rate-per-min}") int limitPerMinute) {
		super(limitPerMinute, "잠시 후 다시 보내 주세요");
	}
}

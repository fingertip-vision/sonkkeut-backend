package com.sonkkeut.backend.stats;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.sonkkeut.backend.common.ApiException;

/**
 * 인증 없이 받는 통계를 한 곳에서 대량으로 보내지 못하게 IP당 1분 전송 수를 제한한다.
 * IP는 메모리에서 1분 동안만 세고 DB에는 남기지 않는다(F-15 익명성).
 */
@Component
public class StatsRateLimiter {

	private static final long WINDOW_NANOS = 60_000_000_000L;
	private static final int CLEANUP_THRESHOLD = 10_000;

	private final int limitPerMinute;
	private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

	public StatsRateLimiter(@Value("${stats.rate-per-min}") int limitPerMinute) {
		this.limitPerMinute = limitPerMinute;
	}

	public void check(String ip) {
		long now = System.nanoTime();
		if (hits.size() > CLEANUP_THRESHOLD) {
			hits.values().removeIf(q -> {
				synchronized (q) {
					return q.isEmpty() || now - q.peekLast() > WINDOW_NANOS;
				}
			});
		}
		Deque<Long> q = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());
		synchronized (q) {
			while (!q.isEmpty() && now - q.peekFirst() > WINDOW_NANOS) {
				q.pollFirst();
			}
			if (q.size() >= limitPerMinute) {
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 보내 주세요");
			}
			q.addLast(now);
		}
	}
}

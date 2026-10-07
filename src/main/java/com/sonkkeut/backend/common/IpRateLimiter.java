package com.sonkkeut.backend.common;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;

/**
 * 인증 없이 받는 요청을 한 곳에서 대량으로 보내지 못하게 IP당 1분 요청 수를 제한한다.
 * IP는 메모리에서 1분 동안만 세고 DB에는 남기지 않는다(F-15 익명성). 기능마다 한도가 달라 하위 클래스로 나눈다.
 */
public abstract class IpRateLimiter {

	private static final long WINDOW_NANOS = 60_000_000_000L;
	private static final int CLEANUP_THRESHOLD = 10_000;

	private final int limitPerMinute;
	private final String detail;
	private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

	protected IpRateLimiter(int limitPerMinute, String detail) {
		this.limitPerMinute = limitPerMinute;
		this.detail = detail;
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
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, detail);
			}
			q.addLast(now);
		}
	}
}

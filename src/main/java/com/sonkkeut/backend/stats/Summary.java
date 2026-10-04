package com.sonkkeut.backend.stats;

import java.util.List;
import java.util.Map;

/** F-15 집계. by_screen은 어느 화면에서 손님이 가장 어려워하는지, fail_reasons는 실패 원인 상위 10개다. */
public record Summary(int sessions, Double completedRate, int steps, Double stepSuccessRate, Double avgReachS,
		Double avgDurationS, Map<String, ScreenStat> byScreen, Map<String, Integer> failReasons, List<Daily> daily) {

	public record ScreenStat(int steps, double successRate, Double avgReachS) {
	}

	public record Daily(String date, int sessions, int completed) {
	}
}

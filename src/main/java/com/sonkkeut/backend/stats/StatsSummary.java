package com.sonkkeut.backend.stats;

import java.util.Map;

/** F-15 집계 결과. failuresByState는 완료하지 못한 주문이 어느 상태에서 끝났는지(실패 지점)다. */
public record StatsSummary(long totalSessions, long completedSessions, double successRate,
		Long averageCompletedDurationMs, Map<AppState, Long> failuresByState, Map<ErrorKind, Long> errorsByKind) {
}

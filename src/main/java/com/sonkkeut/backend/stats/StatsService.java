package com.sonkkeut.backend.stats;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonkkeut.backend.store.Store;
import com.sonkkeut.backend.store.StoreRepository;

@Service
public class StatsService {

	private final UsageSessionRepository sessionRepository;
	private final StoreRepository storeRepository;

	public StatsService(UsageSessionRepository sessionRepository, StoreRepository storeRepository) {
		this.sessionRepository = sessionRepository;
		this.storeRepository = storeRepository;
	}

	@Transactional
	public void record(UsageSessionRequest request) {
		// 앱은 응답을 못 받으면 같은 기록을 다시 보낼 수 있다.
		if (sessionRepository.existsBySessionId(request.sessionId())) {
			return;
		}
		// 매장 코드가 틀려도 기록은 버리지 않는다. 통계 때문에 앱이 오류를 처리할 일은 없어야 한다.
		Store store = request.storeCode() == null ? null
				: storeRepository.findByStoreCode(request.storeCode()).orElse(null);
		List<UsageError> errors = request.errors().stream()
				.map(e -> new UsageError(e.state(), e.kind(), e.elapsedMs()))
				.toList();
		sessionRepository.save(new UsageSession(request.sessionId(), store, request.appVersion(), request.result(),
				request.endState(), request.durationMs(), errors));
	}

	@Transactional(readOnly = true)
	public StatsSummary summary() {
		long total = sessionRepository.count();
		long completed = sessionRepository.countByResult(SessionResult.COMPLETED);
		Double averageMs = sessionRepository.averageDurationMs(SessionResult.COMPLETED);

		Map<AppState, Long> failuresByState = new EnumMap<>(AppState.class);
		for (Object[] row : sessionRepository.countEndStatesExcept(SessionResult.COMPLETED)) {
			failuresByState.put((AppState) row[0], (Long) row[1]);
		}
		Map<ErrorKind, Long> errorsByKind = new EnumMap<>(ErrorKind.class);
		for (Object[] row : sessionRepository.countErrorKinds()) {
			errorsByKind.put((ErrorKind) row[0], (Long) row[1]);
		}
		return new StatsSummary(total, completed, total == 0 ? 0 : (double) completed / total,
				averageMs == null ? null : Math.round(averageMs), failuresByState, errorsByKind);
	}
}

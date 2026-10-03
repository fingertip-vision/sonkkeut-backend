package com.sonkkeut.backend.stats;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsageSessionRepository extends JpaRepository<UsageSession, Long> {

	boolean existsBySessionId(String sessionId);

	long countByResult(SessionResult result);

	@Query("select avg(s.durationMs) from UsageSession s where s.result = :result")
	Double averageDurationMs(SessionResult result);

	@Query("select s.endState, count(s) from UsageSession s where s.result <> :result group by s.endState")
	List<Object[]> countEndStatesExcept(SessionResult result);

	@Query("select e.kind, count(e) from UsageSession s join s.errors e group by e.kind")
	List<Object[]> countErrorKinds();
}

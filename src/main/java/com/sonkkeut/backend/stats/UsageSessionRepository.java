package com.sonkkeut.backend.stats;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageSessionRepository extends JpaRepository<UsageSession, Long> {

	List<UsageSession> findByCreatedAtGreaterThanEqualOrderByIdAsc(Instant since);

	List<UsageSession> findByStoreCodeAndCreatedAtGreaterThanEqualOrderByIdAsc(String storeCode, Instant since);
}

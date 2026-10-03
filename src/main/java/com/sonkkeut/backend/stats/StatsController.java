package com.sonkkeut.backend.stats;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** F-15 익명 사용 통계. 수집은 인증 없이 받고, 요청의 IP 등 보낸 쪽 정보는 저장하지 않는다. */
@RestController
public class StatsController {

	private final StatsService statsService;

	public StatsController(StatsService statsService) {
		this.statsService = statsService;
	}

	@PostMapping("/api/v1/stats/sessions")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void record(@Valid @RequestBody UsageSessionRequest request) {
		statsService.record(request);
	}

	@GetMapping("/api/v1/stats/summary")
	public StatsSummary summary() {
		return statsService.summary();
	}
}

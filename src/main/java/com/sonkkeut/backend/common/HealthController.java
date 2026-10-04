package com.sonkkeut.backend.common;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** PR #1과 같은 상태 확인 경로. 배포 스크립트는 /actuator/health를 그대로 쓴다. */
@RestController
public class HealthController {

	private final JdbcTemplate jdbcTemplate;

	public HealthController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping("/healthz")
	public Map<String, Object> healthz() {
		jdbcTemplate.queryForObject("select 1", Integer.class);
		return Map.of("ok", true, "db", "mysql");
	}
}

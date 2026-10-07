package com.sonkkeut.backend.stats;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sonkkeut.backend.common.ApiException;
import com.sonkkeut.backend.common.ClientIp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** F-15 익명 사용 통계. 보내기는 누구나, 보기는 점주 키(그 매장)나 운영자 키(전체)가 있어야 한다. */
@RestController
public class StatsController {

	private final StatsService statsService;

	public StatsController(StatsService statsService) {
		this.statsService = statsService;
	}

	@PostMapping("/api/stats/sessions")
	@ResponseStatus(HttpStatus.CREATED)
	public Map<String, Boolean> record(@Valid @RequestBody SessionIn body, HttpServletRequest request) {
		// 재전송도 성공으로 답해야 앱이 같은 기록을 계속 다시 보내지 않는다.
		return statsService.record(body, ClientIp.of(request)) ? Map.of("ok", true)
				: Map.of("ok", true, "duplicate", true);
	}

	@GetMapping("/api/stats/summary")
	public Summary summary(@RequestParam(name = "store_code", required = false) String storeCode,
			@RequestParam(defaultValue = "30") int days,
			@RequestHeader(name = "X-Owner-Key", required = false) String ownerKey,
			@RequestHeader(name = "X-Admin-Key", required = false) String adminKey) {
		if (days < 1 || days > 365) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "days는 1~365 사이여야 합니다");
		}
		return statsService.summary(storeCode == null || storeCode.isBlank() ? null : storeCode, days, ownerKey,
				adminKey);
	}
}

package com.sonkkeut.backend.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 한도를 3으로 낮춘 별도 컨텍스트에서, 같은 IP의 네 번째 전송부터 429가 되는지 본다. */
@SpringBootTest(properties = "stats.rate-per-min=3")
@AutoConfigureMockMvc
class StatsRateLimitTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void IP당_1분_한도를_넘으면_429() throws Exception {
		// 다른 테스트와 같은 주소로 세지 않게 테스트 전용 주소를 쓴다.
		String ip = "203.0.113." + (UUID.randomUUID().hashCode() & 0xff);
		List<Integer> codes = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			codes.add(mockMvc.perform(post("/api/stats/sessions").header("X-Forwarded-For", ip + ", 10.0.0.1")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"completed\": false, \"duration_s\": 1}"))
					.andReturn().getResponse().getStatus());
		}
		assertThat(codes.subList(0, 3)).containsOnly(201);
		assertThat(codes.get(3)).isEqualTo(429);
	}
}

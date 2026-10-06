package com.sonkkeut.backend.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 한도를 3으로 낮춘 별도 컨텍스트에서, 인증 없이 받는 통계·매장 등록의 IP당 1분 한도를 본다. */
@SpringBootTest(properties = { "stats.rate-per-min=3", "store.create-rate-per-min=3" })
@AutoConfigureMockMvc
class RateLimitTest {

	@Autowired
	private MockMvc mockMvc;

	// 테스트끼리 같은 주소로 세지 않게 테스트마다 다른 주소를 쓴다.
	private static String newIp() {
		return "203.0.113." + (UUID.randomUUID().hashCode() & 0xff);
	}

	private List<Integer> statusCodes(int times, IntFunction<MockHttpServletRequestBuilder> request) throws Exception {
		List<Integer> codes = new ArrayList<>();
		for (int i = 0; i < times; i++) {
			codes.add(mockMvc.perform(request.apply(i)).andReturn().getResponse().getStatus());
		}
		return codes;
	}

	private static MockHttpServletRequestBuilder stats() {
		return post("/api/stats/sessions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"completed\": false, \"duration_s\": 1}");
	}

	@Test
	void 통계는_IP당_1분_한도를_넘으면_429() throws Exception {
		String ip = newIp();
		List<Integer> codes = statusCodes(5, i -> stats().header("CF-Connecting-IP", ip));
		assertThat(codes.subList(0, 3)).containsOnly(201);
		assertThat(codes.get(3)).isEqualTo(429);
	}

	@Test
	void X_Forwarded_For를_바꿔_보내도_한도를_피할_수_없다() throws Exception {
		// 예전에는 X-Forwarded-For 첫 값을 IP로 써서, 요청마다 값을 바꾸면 한도를 무시할 수 있었다.
		String ip = newIp();
		List<Integer> codes = statusCodes(5, i -> stats().header("CF-Connecting-IP", ip)
				.header("X-Forwarded-For", "198.51.100." + i));
		assertThat(codes.get(3)).isEqualTo(429);
	}

	@Test
	void 매장_등록도_IP당_1분_한도를_넘으면_429() throws Exception {
		String ip = newIp();
		List<Integer> codes = statusCodes(5, i -> post("/api/stores").header("CF-Connecting-IP", ip)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"한도 확인 매장\"}"));
		assertThat(codes.subList(0, 3)).containsOnly(201);
		assertThat(codes.get(3)).isEqualTo(429);
	}
}

package com.sonkkeut.backend.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** F-15 익명 통계를 PR #1(FastAPI)의 테스트와 같은 항목으로 확인한다. 집계는 테스트마다 새로 만든 매장으로 본다. */
@SpringBootTest(properties = "sonkkeut.admin-key=admin-test")
@AutoConfigureMockMvc
class StatsApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	private JsonNode makeStore() throws Exception {
		String response = mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"카페 손끝\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(response);
	}

	private ResultActions send(String body) throws Exception {
		return mockMvc.perform(post("/api/stats/sessions").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions storeSummary(JsonNode store) throws Exception {
		return mockMvc.perform(get("/api/stats/summary").param("store_code", store.get("code").asString())
				.header("X-Owner-Key", store.get("owner_key").asString()));
	}

	private static String eventId() {
		return "session-" + UUID.randomUUID();
	}

	@Test
	void 단계별_기록을_매장별로_집계한다() throws Exception {
		JsonNode store = makeStore();
		String body = """
				{"store_code": "%s", "app_version": "0.1.0", "completed": true, "duration_s": 42.5,
				 "steps": [
				   {"screen_type": "menu", "target_kind": "tab", "result": "success", "reach_s": 3.2, "hints": 4},
				   {"screen_type": "menu", "target_kind": "menu", "result": "fail", "reach_s": 6.0, "fail_reason": "no_change"},
				   {"screen_type": "option", "target_kind": "button", "result": "success", "reach_s": 2.0}
				 ]}
				""".formatted(store.get("code").asString().toLowerCase());
		send(body).andExpect(status().isCreated()).andExpect(jsonPath("$.ok").value(true));
		send(body.replace("\"completed\": true", "\"completed\": false").replaceAll("(?s)\"steps\": \\[.*]", "\"steps\": []"))
				.andExpect(status().isCreated());
		// 정의되지 않은 판정 값은 거부
		send("{\"completed\": true, \"duration_s\": 1, \"steps\": [{\"result\": \"maybe\"}]}")
				.andExpect(status().isUnprocessableContent());

		mockMvc.perform(get("/api/stats/summary").param("store_code", store.get("code").asString()))
				.andExpect(status().isUnauthorized());
		storeSummary(store)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sessions").value(2))
				.andExpect(jsonPath("$.completed_rate").value(0.5))
				.andExpect(jsonPath("$.steps").value(3))
				.andExpect(jsonPath("$.by_screen.menu.success_rate").value(0.5))
				.andExpect(jsonPath("$.by_screen.menu.steps").value(2))
				.andExpect(jsonPath("$.fail_reasons.no_change").value(1))
				.andExpect(jsonPath("$.fail_reasons.length()").value(1))
				.andExpect(jsonPath("$.avg_reach_s").value(3.73))
				.andExpect(jsonPath("$.daily[0].sessions").value(2))
				.andExpect(jsonPath("$.daily[0].completed").value(1));

		mockMvc.perform(get("/api/stats/summary")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("전체 통계는 운영자 키가 필요합니다"));
		String all = mockMvc.perform(get("/api/stats/summary").header("X-Admin-Key", "admin-test"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(jsonMapper.readTree(all).get("sessions").asInt()).isGreaterThanOrEqualTo(2);
	}

	@Test
	void 재전송은_한_번만_세고_같은_ID에_다른_내용은_409() throws Exception {
		JsonNode store = makeStore();
		String body = """
				{"event_id": "%s", "store_code": "%s", "completed": true, "duration_s": 20,
				 "steps": [{"screen_type": "menu", "target_kind": "menu", "result": "success"}]}
				""".formatted(eventId(), store.get("code").asString());
		send(body).andExpect(status().isCreated()).andExpect(jsonPath("$.duplicate").doesNotExist());
		send(body).andExpect(status().isCreated()).andExpect(jsonPath("$.duplicate").value(true));
		storeSummary(store).andExpect(jsonPath("$.sessions").value(1));

		send(body.replace("\"completed\": true", "\"completed\": false"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("다른 통계에 같은 이벤트 ID를 사용할 수 없습니다"));
	}

	@Test
	void 동시에_들어온_재전송도_한_번만_센다() throws Exception {
		JsonNode store = makeStore();
		String body = "{\"event_id\": \"%s\", \"store_code\": \"%s\", \"completed\": true, \"duration_s\": 10}"
				.formatted(eventId(), store.get("code").asString());
		ExecutorService workers = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> results = workers.invokeAll(List.of(
					() -> send(body).andReturn().getResponse().getStatus(),
					() -> send(body).andReturn().getResponse().getStatus()));
			for (Future<Integer> result : results) {
				assertThat(result.get()).isEqualTo(201);
			}
		} finally {
			workers.shutdown();
		}
		storeSummary(store).andExpect(jsonPath("$.sessions").value(1));
	}

	@Test
	void 개인정보가_될_수_있는_필드와_유한하지_않은_숫자는_거부한다() throws Exception {
		for (String field : List.of("image", "audio", "device_id", "location", "transcript")) {
			send("{\"completed\": false, \"duration_s\": 10, \"%s\": \"private\"}".formatted(field))
					.andExpect(status().isUnprocessableContent())
					.andExpect(jsonPath("$.detail[0].loc[1]").value(field))
					.andExpect(jsonPath("$.detail[0].type").value("extra_forbidden"));
		}
		send("{\"completed\": false, \"duration_s\": 10, \"steps\": [{\"result\": \"success\", \"finger\": [0, 0]}]}")
				.andExpect(status().isUnprocessableContent());
		send("{\"completed\": false, \"duration_s\": \"NaN\"}").andExpect(status().isUnprocessableContent());
		send("{\"completed\": false, \"duration_s\": 10, \"steps\": [{\"result\": \"success\", \"reach_s\": \"NaN\"}]}")
				.andExpect(status().isUnprocessableContent());
		send("{\"event_id\": \"short\", \"completed\": false, \"duration_s\": 10}")
				.andExpect(status().isUnprocessableContent());
	}

	@Test
	void 포장_매장_선택_단계도_재전송시_한_번만_집계한다() throws Exception {
		JsonNode store = makeStore();
		String body = """
				{"event_id":"%s", "store_code":"%s", "app_version":"0.1.2",
				 "model_version":"2026.10.03", "completed":true, "duration_s":20,
				 "steps":[{"screen_type":"method", "target_kind":"button", "result":"success"}]}
				""".formatted(eventId(), store.get("code").asString());
		send(body).andExpect(status().isCreated());
		send(body).andExpect(status().isCreated()).andExpect(jsonPath("$.duplicate").value(true));
		storeSummary(store).andExpect(jsonPath("$.sessions").value(1))
				.andExpect(jsonPath("$.by_screen.method.steps").value(1))
				.andExpect(jsonPath("$.by_screen.method.success_rate").value(1.0));
	}

	@Test
	void 모르는_매장_코드는_버리고_기록만_남긴다() throws Exception {
		send("{\"store_code\": \"NOSUCH\", \"completed\": true, \"duration_s\": 5}").andExpect(status().isCreated());
	}
}

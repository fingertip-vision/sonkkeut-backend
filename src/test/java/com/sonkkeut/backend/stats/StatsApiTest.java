package com.sonkkeut.backend.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** F-15 기록 수집 → 집계를 실제 DB와 함께 확인한다. DB에 이전 기록이 남아 있으므로 집계는 전후 차이로 본다. */
@SpringBootTest
@AutoConfigureMockMvc
class StatsApiTest {

	private static final String FAILED_SESSION = """
			{
			  "sessionId": "%s",
			  "appVersion": "0.1.0",
			  "result": "FAILED",
			  "endState": "SE",
			  "durationMs": 42000,
			  "errors": [
			    { "state": "S5", "kind": "WRONG_PRESS", "elapsedMs": 30000 },
			    { "state": "S4", "kind": "TIMEOUT", "elapsedMs": 41000 }
			  ]
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	private ResultActions send(String body) throws Exception {
		return mockMvc.perform(post("/api/v1/stats/sessions").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private JsonNode summary() throws Exception {
		String body = mockMvc.perform(get("/api/v1/stats/summary"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(body);
	}

	@Test
	void 실패한_주문을_보내면_실패_지점과_예외_종류가_집계된다() throws Exception {
		JsonNode before = summary();

		send(FAILED_SESSION.formatted(UUID.randomUUID())).andExpect(status().isNoContent());

		JsonNode after = summary();
		assertThat(after.get("totalSessions").asLong()).isEqualTo(before.get("totalSessions").asLong() + 1);
		assertThat(after.get("completedSessions").asLong()).isEqualTo(before.get("completedSessions").asLong());
		assertThat(after.at("/failuresByState/SE").asLong()).isEqualTo(before.at("/failuresByState/SE").asLong(0) + 1);
		assertThat(after.at("/errorsByKind/WRONG_PRESS").asLong())
				.isEqualTo(before.at("/errorsByKind/WRONG_PRESS").asLong(0) + 1);
	}

	@Test
	void 같은_기록을_다시_보내도_한_번만_센다() throws Exception {
		String body = FAILED_SESSION.formatted(UUID.randomUUID());
		send(body).andExpect(status().isNoContent());
		long total = summary().get("totalSessions").asLong();

		send(body).andExpect(status().isNoContent());

		assertThat(summary().get("totalSessions").asLong()).isEqualTo(total);
	}

	@Test
	void 완료한_주문은_매장_코드가_틀려도_기록된다() throws Exception {
		long completed = summary().get("completedSessions").asLong();

		send("""
				{ "sessionId": "%s", "storeCode": "NOSUCH", "appVersion": "0.1.0",
				  "result": "COMPLETED", "endState": "S6", "durationMs": 83000 }
				""".formatted(UUID.randomUUID())).andExpect(status().isNoContent());

		assertThat(summary().get("completedSessions").asLong()).isEqualTo(completed + 1);
	}

	@Test
	void 명세에_없는_상태_이름은_400() throws Exception {
		send("""
				{ "sessionId": "%s", "appVersion": "0.1.0", "result": "COMPLETED", "endState": "S9", "durationMs": 1 }
				""".formatted(UUID.randomUUID()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("malformed request body"));
	}

	@Test
	void 세션_ID가_UUID가_아니면_400() throws Exception {
		send("""
				{ "sessionId": "user-1234", "appVersion": "0.1.0", "result": "COMPLETED", "endState": "S6",
				  "durationMs": 1 }
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid field: sessionId"));
	}
}

package com.sonkkeut.backend.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * F-14 매장·메뉴와 모델 정보 API를 PR #1(FastAPI)의 테스트와 같은 항목으로 확인한다.
 * 실제 MySQL에 이전 실행의 데이터가 남아 있으므로, 테스트마다 매장을 새로 만들어 그 매장만 본다.
 */
// 매장을 여러 개 만드는 테스트라 등록 한도는 넉넉히 푼다. 한도 자체는 RateLimitTest에서 본다.
@SpringBootTest(properties = { "sonkkeut.admin-key=admin-test", "store.create-rate-per-min=1000" })
@AutoConfigureMockMvc
class StoreMenuApiTest {

	private static final String ITEMS = """
			{"items": [
			  {"category": "커피", "name": "아메리카노", "price": 4500, "aliases": ["아아", " 아아 ", ""],
			   "options": [{"group": "온도", "values": ["HOT", "ICE"]}]},
			  {"category": "커피", "name": "카페라떼", "price": 5000},
			  {"category": "디저트", "name": "치즈케이크", "price": 6500, "sold_out": true}
			]}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	private JsonNode makeStore(String extraJson) throws Exception {
		String body = "{\"name\": \"카페 손끝\", \"lat\": 35.83, \"lng\": 128.75" + extraJson + "}";
		String response = mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(response);
	}

	private ResultActions putMenu(String code, String body, String header, String key) throws Exception {
		var request = put("/api/stores/" + code + "/menu").contentType(MediaType.APPLICATION_JSON).content(body);
		if (header != null) {
			request.header(header, key);
		}
		return mockMvc.perform(request);
	}

	@Test
	void 상태_확인과_화면() throws Exception {
		mockMvc.perform(get("/healthz")).andExpect(jsonPath("$.ok").value(true));
		mockMvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(header().string("Location", "/owner"));
		mockMvc.perform(get("/owner")).andExpect(status().isOk()).andExpect(forwardedUrl("/static/owner.html"));
		mockMvc.perform(get("/dashboard")).andExpect(forwardedUrl("/static/dashboard.html"));
		mockMvc.perform(get("/kiosk")).andExpect(forwardedUrl("/static/kiosk.html"));
		mockMvc.perform(get("/simulation")).andExpect(forwardedUrl("/static/simulation.html"));
		for (String asset : List.of("simulation.html", "simulation.js", "simulation.css")) {
			mockMvc.perform(get("/static/" + asset)).andExpect(status().isOk());
		}
		// 정적 HTML 응답에는 charset이 없어 MockMvc가 ISO-8859-1로 읽으므로, 브라우저처럼 문서의 UTF-8로 읽는다.
		mockMvc.perform(get("/static/owner.html")).andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
						.contains("메뉴 등록"));
		mockMvc.perform(get("/static/owner.js")).andExpect(status().isOk());
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	@Test
	void 매장을_만들고_메뉴를_저장하면_앱이_사전을_받는다() throws Exception {
		JsonNode store = makeStore("");
		String code = store.get("code").asString();
		String key = store.get("owner_key").asString();
		assertThat(code).hasSize(6);
		assertThat(store.get("menu_version").asInt()).isZero();

		putMenu(code, ITEMS, null, null).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("점주 키가 맞지 않습니다"));
		putMenu(code, ITEMS, "X-Owner-Key", "wrong").andExpect(status().isUnauthorized());
		putMenu(code, ITEMS, "X-Owner-Key", key)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.menu_version").value(1))
				.andExpect(jsonPath("$.categories[0]").value("커피"))
				.andExpect(jsonPath("$.categories[1]").value("디저트"))
				.andExpect(jsonPath("$.categories.length()").value(2))
				.andExpect(jsonPath("$.items[0].aliases.length()").value(1))
				.andExpect(jsonPath("$.items[0].aliases[0]").value("아아"))
				.andExpect(jsonPath("$.items[0].options[0].group").value("온도"))
				.andExpect(jsonPath("$.items[0].id").isNumber())
				.andExpect(jsonPath("$.items[2].sold_out").value(true));

		// 공개 조회 + ETag. 매장 코드는 소문자로 불러도 찾는다.
		String etag = mockMvc.perform(get("/api/stores/" + code.toLowerCase() + "/menu"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(3))
				.andExpect(jsonPath("$.store_code").value(code))
				.andReturn().getResponse().getHeader("ETag");
		mockMvc.perform(get("/api/stores/" + code + "/menu").header("If-None-Match", etag))
				.andExpect(status().isNotModified());

		// 다시 저장하면 버전이 오르고 이전 항목은 지워진다.
		putMenu(code, "{\"items\": [{\"category\": \"커피\", \"name\": \"아메리카노\", \"price\": 4500}]}",
				"X-Owner-Key", key)
				.andExpect(jsonPath("$.menu_version").value(2))
				.andExpect(jsonPath("$.items.length()").value(1));

		// 같은 이름 거부
		putMenu(code, "{\"items\": [{\"name\": \"카페라떼\"}, {\"name\": \"카페라떼\"}]}", "X-Owner-Key", key)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail").value("같은 이름의 메뉴가 있습니다: 카페라떼"));

		// 운영자 키로도 수정 가능
		mockMvc.perform(patch("/api/stores/" + code).header("X-Admin-Key", "admin-test")
						.contentType(MediaType.APPLICATION_JSON).content("{\"address\": \"경산시\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.address").value("경산시"));
	}

	@Test
	void 공백뿐인_메뉴_이름은_422() throws Exception {
		JsonNode store = makeStore("");
		putMenu(store.get("code").asString(), "{\"items\": [{\"name\": \"   \"}]}", "X-Owner-Key",
				store.get("owner_key").asString())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail[0].loc[1]").value("items"))
				.andExpect(jsonPath("$.detail[0].loc[2]").value(0))
				.andExpect(jsonPath("$.detail[0].loc[3]").value("name"));
	}

	@Test
	void 점주_화면은_빈_PATCH로_키를_확인한다() throws Exception {
		JsonNode store = makeStore("");
		String code = store.get("code").asString();
		mockMvc.perform(patch("/api/stores/" + code).header("X-Owner-Key", store.get("owner_key").asString())
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("카페 손끝"));
		mockMvc.perform(patch("/api/stores/" + code).header("X-Owner-Key", "wrong")
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(patch("/api/stores/" + code).header("X-Owner-Key", store.get("owner_key").asString())
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail[0].loc[1]").value("name"));
	}

	@Test
	void 근처_매장() throws Exception {
		// 이전 실행에서 만든 매장과 겹치지 않게 매번 다른 위치를 쓴다.
		double lat = 35 + ThreadLocalRandom.current().nextDouble(0, 1);
		double lng = 128 + ThreadLocalRandom.current().nextDouble(0, 1);
		String near = makeStore(", \"lat\": %f, \"lng\": %f".formatted(lat, lng).replace("\"lat\": 35.83, ", ""))
				.get("code").asString();
		String far = makeStore(", \"lat\": %f, \"lng\": %f".formatted(lat + 0.07, lng + 0.15)).get("code").asString();
		makeStore(", \"lat\": null, \"lng\": null");

		String body = mockMvc.perform(get("/api/stores/nearby")
						.param("lat", String.valueOf(lat + 0.0001))
						.param("lng", String.valueOf(lng + 0.0001))
						.param("radius_m", "300"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<JsonNode> stores = jsonMapper.readTree(body).valueStream().toList();
		assertThat(stores).extracting(s -> s.get("code").asString()).contains(near).doesNotContain(far);
		assertThat(stores).allSatisfy(s -> assertThat(s.get("distance_m").asDouble()).isLessThanOrEqualTo(300));

		mockMvc.perform(get("/api/stores/nearby").param("lng", "128")).andExpect(status().isUnprocessableContent());
		mockMvc.perform(get("/api/stores/nearby").param("lat", "35").param("lng", "128").param("radius_m", "9999"))
				.andExpect(status().isUnprocessableContent());
	}

	@Test
	void 전체_매장_목록은_운영자만_보고_모델_정보는_누구나_본다() throws Exception {
		mockMvc.perform(get("/api/stores")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/stores").header("X-Admin-Key", "admin-test"))
				.andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
		mockMvc.perform(get("/api/models/latest"))
				.andExpect(jsonPath("$.version").value("2026.10.03"))
				.andExpect(jsonPath("$.files.length()").value(5))
				.andExpect(jsonPath("$.files[0].name").value("m1_screen_corners_int8.onnx"))
				.andExpect(jsonPath("$.files[0].url").doesNotExist())
				.andExpect(jsonPath("$.files[0].sha256", Matchers.matchesPattern("[0-9a-f]{64}")))
				.andExpect(jsonPath("$.files[0].size_bytes", Matchers.greaterThan(1_000_000)));
	}

	@Test
	void 매장_삭제() throws Exception {
		JsonNode store = makeStore("");
		String code = store.get("code").asString();
		mockMvc.perform(delete("/api/stores/" + code)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/stores/" + code).header("X-Owner-Key", store.get("owner_key").asString()))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/stores/" + code)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("매장을 찾을 수 없습니다"));
	}

	@Test
	void 브라우저에서_점주_키를_실은_메뉴_저장을_허용한다() throws Exception {
		mockMvc.perform(options("/api/stores/ABCDEF/menu")
						.header("Origin", "http://localhost:5173")
						.header("Access-Control-Request-Method", "PUT")
						.header("Access-Control-Request-Headers", "x-owner-key,content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	void Spring이_정한_오류도_detail_형식으로_답한다() throws Exception {
		mockMvc.perform(delete("/api/stats/sessions"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(header().exists("Allow"))
				.andExpect(jsonPath("$.detail").isString());
		mockMvc.perform(post("/api/stores").contentType(MediaType.TEXT_PLAIN).content("카페"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.detail").isString());
		mockMvc.perform(get("/api/no-such-path"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").isString());
	}
}

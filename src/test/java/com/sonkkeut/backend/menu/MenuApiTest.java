package com.sonkkeut.backend.menu;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** F-14 매장·메뉴 등록 → 앱의 사전 조회까지를 실제 DB와 함께 확인한다. 테스트마다 매장을 새로 만들어 서로 섞이지 않게 한다. */
@SpringBootTest
@AutoConfigureMockMvc
class MenuApiTest {

	private static final String AMERICANO = """
			{
			  "categoryId": %d,
			  "name": "아메리카노",
			  "price": 4500,
			  "aliases": ["아아", "아메"],
			  "options": { "temp": ["hot", "ice"], "size": ["regular", "large"] }
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	private JsonNode createStore() throws Exception {
		return postJson("/api/v1/stores", "{\"name\": \"테스트 카페\"}");
	}

	private long createCategory(long storeId, String name) throws Exception {
		return postJson("/api/v1/stores/" + storeId + "/categories", "{\"name\": \"" + name + "\", \"sortOrder\": 1}")
				.get("id").asLong();
	}

	private JsonNode postJson(String url, String body) throws Exception {
		String response = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(response);
	}

	@Test
	void 등록한_메뉴가_매장_코드로_조회한_사전에_나온다() throws Exception {
		JsonNode store = createStore();
		long storeId = store.get("id").asLong();
		long categoryId = createCategory(storeId, "커피");
		postJson("/api/v1/stores/" + storeId + "/menus", AMERICANO.formatted(categoryId));

		mockMvc.perform(get("/api/v1/dictionaries/" + store.get("storeCode").asString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.storeName").value("테스트 카페"))
				.andExpect(jsonPath("$.categories[0].name").value("커피"))
				.andExpect(jsonPath("$.categories[0].menus[0].name").value("아메리카노"))
				.andExpect(jsonPath("$.categories[0].menus[0].price").value(4500))
				.andExpect(jsonPath("$.categories[0].menus[0].aliases[0]").value("아아"))
				.andExpect(jsonPath("$.categories[0].menus[0].options.temp[1]").value("ice"));
	}

	@Test
	void 메뉴가_바뀌면_사전_버전이_올라가고_같은_버전이면_304() throws Exception {
		JsonNode store = createStore();
		long storeId = store.get("id").asLong();
		String dictionaryUrl = "/api/v1/dictionaries/" + store.get("storeCode").asString();
		long categoryId = createCategory(storeId, "커피");

		mockMvc.perform(get(dictionaryUrl))
				.andExpect(jsonPath("$.version").value(2))
				.andExpect(header().string("ETag", "\"2\""));
		mockMvc.perform(get(dictionaryUrl).header("If-None-Match", "\"2\""))
				.andExpect(status().isNotModified());

		postJson("/api/v1/stores/" + storeId + "/menus", AMERICANO.formatted(categoryId));

		mockMvc.perform(get(dictionaryUrl).header("If-None-Match", "\"2\""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(3));
	}

	@Test
	void 메뉴를_수정하고_삭제할_수_있다() throws Exception {
		long storeId = createStore().get("id").asLong();
		long categoryId = createCategory(storeId, "커피");
		long menuId = postJson("/api/v1/stores/" + storeId + "/menus", AMERICANO.formatted(categoryId))
				.get("id").asLong();

		mockMvc.perform(put("/api/v1/stores/" + storeId + "/menus/" + menuId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"categoryId\": %d, \"name\": \"아메리카노\", \"price\": 5000, \"soldOut\": true}"
								.formatted(categoryId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.price").value(5000))
				.andExpect(jsonPath("$.soldOut").value(true))
				.andExpect(jsonPath("$.aliases").isEmpty());

		mockMvc.perform(delete("/api/v1/stores/" + storeId + "/menus/" + menuId))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/stores/" + storeId + "/menus"))
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void 메뉴가_남아_있는_카테고리는_지울_수_없다() throws Exception {
		long storeId = createStore().get("id").asLong();
		long categoryId = createCategory(storeId, "커피");
		postJson("/api/v1/stores/" + storeId + "/menus", AMERICANO.formatted(categoryId));

		mockMvc.perform(delete("/api/v1/stores/" + storeId + "/categories/" + categoryId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("category not empty"));
	}

	@Test
	void 다른_매장의_카테고리에는_메뉴를_등록할_수_없다() throws Exception {
		long storeId = createStore().get("id").asLong();
		long otherCategoryId = createCategory(createStore().get("id").asLong(), "커피");

		mockMvc.perform(post("/api/v1/stores/" + storeId + "/menus")
						.contentType(MediaType.APPLICATION_JSON)
						.content(AMERICANO.formatted(otherCategoryId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("category not found"));
	}

	@Test
	void 가격이_음수면_400() throws Exception {
		long storeId = createStore().get("id").asLong();
		long categoryId = createCategory(storeId, "커피");

		mockMvc.perform(post("/api/v1/stores/" + storeId + "/menus")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"categoryId\": %d, \"name\": \"아메리카노\", \"price\": -1}".formatted(categoryId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid field: price"));
	}

	@Test
	void 없는_매장_코드는_404() throws Exception {
		mockMvc.perform(get("/api/v1/dictionaries/NOSUCH"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("store not found"));
	}

	@Test
	void 브라우저의_메뉴_수정_사전_요청을_허용한다() throws Exception {
		mockMvc.perform(options("/api/v1/stores/1/menus/1")
						.header("Origin", "http://localhost:5173")
						.header("Access-Control-Request-Method", "PUT"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	void 브라우저가_사전_응답의_ETag를_읽을_수_있다() throws Exception {
		String storeCode = createStore().get("storeCode").asString();

		mockMvc.perform(get("/api/v1/dictionaries/" + storeCode).header("Origin", "http://localhost:5173"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Expose-Headers", "ETag"));
	}
}

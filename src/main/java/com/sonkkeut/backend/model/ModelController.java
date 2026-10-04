package com.sonkkeut.backend.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 모델 버전 안내. 서버는 AI 계산을 하지 않고, 앱에 든 모델 파일이 맞는지 확인할 SHA-256·크기만 알려 준다.
 * 해시와 크기는 AI 저장소의 모델 파일에서 뽑은 model-manifest.json을 그대로 쓴다.
 */
@RestController
public class ModelController {

	// 앱이 쓰는 순서대로 고정한다. 매니페스트에 없는 모델은 해시·크기를 null로 돌려준다.
	private static final List<String> MODEL_NAMES = List.of("m1_screen_corners_int8.onnx",
			"m2_screen_elements_int8.onnx", "m1r_corner_refiner.onnx");

	private final String version;
	private final String baseUrl;
	private final JsonNode manifest;

	public ModelController(@Value("${model.version}") String version, @Value("${model.base-url}") String baseUrl,
			JsonMapper jsonMapper) {
		this.version = version;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		try (InputStream in = new ClassPathResource("web/model-manifest.json").getInputStream()) {
			this.manifest = jsonMapper.readTree(in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@GetMapping("/api/models/latest")
	public ModelInfo latest() {
		List<ModelInfo.ModelFile> files = MODEL_NAMES.stream().map(name -> {
			JsonNode record = find(name);
			return new ModelInfo.ModelFile(name, baseUrl.isEmpty() ? null : baseUrl + "/" + name,
					record == null ? null : record.get("sha256").asString(),
					record == null ? null : record.get("size_bytes").asLong());
		}).toList();
		return new ModelInfo(version, files);
	}

	private JsonNode find(String name) {
		for (JsonNode model : manifest.path("models")) {
			if (name.equals(model.path("name").asString())) {
				return model;
			}
		}
		return null;
	}
}

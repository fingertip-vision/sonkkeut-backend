package com.sonkkeut.backend.common;

import jakarta.persistence.AttributeConverter;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 목록 값을 JSON 문자열 한 칸에 저장한다. 메뉴 별칭·옵션과 통계 단계는 항상 통째로 읽고 쓰기 때문에
 * 테이블을 나누지 않는다. API 응답의 snake_case 설정과 섞이지 않게 변환기 전용 매퍼를 쓴다.
 */
public abstract class JsonColumnConverter<T> implements AttributeConverter<T, String> {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final TypeReference<T> type;

	protected JsonColumnConverter(TypeReference<T> type) {
		this.type = type;
	}

	@Override
	public String convertToDatabaseColumn(T value) {
		return value == null ? null : MAPPER.writeValueAsString(value);
	}

	@Override
	public T convertToEntityAttribute(String json) {
		return json == null ? null : MAPPER.readValue(json, type);
	}
}

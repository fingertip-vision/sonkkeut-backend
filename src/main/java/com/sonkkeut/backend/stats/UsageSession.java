package com.sonkkeut.backend.stats;

import java.time.Instant;
import java.util.List;

import com.sonkkeut.backend.common.JsonColumnConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import tools.jackson.core.type.TypeReference;

/**
 * F-15 익명 사용 기록 한 번. 수용 기준이 "개인을 식별할 수 있는 정보 없음"이라
 * 영상·음성·위치·기기 식별자·IP는 저장하지 않는다. 매장은 FK가 아닌 코드로 남겨, 매장을 지워도 기록은 남는다.
 */
@Entity
@Table(name = "usage_sessions", indexes = {
		@Index(columnList = "storeCode"),
		@Index(columnList = "createdAt") })
public class UsageSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 12)
	private String storeCode;

	@Column(nullable = false, length = 20)
	private String appVersion;

	@Column(nullable = false, length = 20)
	private String modelVersion;

	@Column(nullable = false)
	private boolean completed;

	@Column(nullable = false)
	private double durationS;

	@Column(nullable = false)
	private int nSteps;

	@Convert(converter = StepsConverter.class)
	@Column(nullable = false, columnDefinition = "text")
	private List<StepIn> steps;

	@Column(nullable = false)
	private Instant createdAt;

	protected UsageSession() {
	}

	UsageSession(String storeCode, SessionIn in) {
		this.storeCode = storeCode;
		this.appVersion = in.appVersion();
		this.modelVersion = in.modelVersion();
		this.completed = in.completed();
		this.durationS = in.durationS();
		this.nSteps = in.steps().size();
		this.steps = in.steps();
		this.createdAt = Instant.now();
	}

	public boolean isCompleted() {
		return completed;
	}

	public double getDurationS() {
		return durationS;
	}

	public List<StepIn> getSteps() {
		return steps;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	@Converter
	static class StepsConverter extends JsonColumnConverter<List<StepIn>> {
		StepsConverter() {
			super(new TypeReference<>() {
			});
		}
	}
}

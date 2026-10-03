package com.sonkkeut.backend.stats;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.sonkkeut.backend.store.Store;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * F-15 주문 한 번의 익명 기록. 수용 기준이 "개인을 식별할 수 있는 정보 없음"이라
 * 사용자·기기 식별자, IP, 발화 내용, 주문한 메뉴는 받지도 저장하지도 않는다.
 */
@Entity
@Table(name = "usage_session")
public class UsageSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 앱이 주문마다 새로 만드는 무작위 값. 재전송된 기록을 두 번 세지 않으려고만 쓴다.
	@Column(nullable = false, unique = true, length = 36)
	private String sessionId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "store_id")
	private Store store;

	@Column(nullable = false, length = 20)
	private String appVersion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SessionResult result;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private AppState endState;

	@Column(nullable = false)
	private long durationMs;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@ElementCollection
	@CollectionTable(name = "usage_error", joinColumns = @JoinColumn(name = "usage_session_id"))
	private List<UsageError> errors = new ArrayList<>();

	protected UsageSession() {
	}

	public UsageSession(String sessionId, Store store, String appVersion, SessionResult result, AppState endState,
			long durationMs, List<UsageError> errors) {
		this.sessionId = sessionId;
		this.store = store;
		this.appVersion = appVersion;
		this.result = result;
		this.endState = endState;
		this.durationMs = durationMs;
		this.createdAt = LocalDateTime.now();
		this.errors.addAll(errors);
	}
}

package com.sonkkeut.backend.store;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** F-14 메뉴를 등록하는 매장. 앱은 내부 ID 대신 storeCode로 메뉴 사전을 찾는다. */
@Entity
@Table(name = "store")
public class Store {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, unique = true, length = 6)
	private String storeCode;

	// 앱이 캐시한 사전이 최신인지 비교하는 값. 카테고리나 메뉴가 바뀔 때마다 올린다.
	@Column(nullable = false)
	private int dictionaryVersion;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	protected Store() {
	}

	public Store(String name, String storeCode) {
		this.name = name;
		this.storeCode = storeCode;
		this.dictionaryVersion = 1;
		this.createdAt = LocalDateTime.now();
	}

	public void touchDictionary() {
		this.dictionaryVersion++;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getStoreCode() {
		return storeCode;
	}

	public int getDictionaryVersion() {
		return dictionaryVersion;
	}
}

package com.sonkkeut.backend.menu;

import com.sonkkeut.backend.store.Store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 키오스크 메뉴 화면의 탭("커피" 등)에 대응한다. F-07이 탭 → 메뉴 순서로 버튼을 고른다. */
@Entity
@Table(name = "menu_category")
public class MenuCategory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "store_id")
	private Store store;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false)
	private int sortOrder;

	protected MenuCategory() {
	}

	public MenuCategory(Store store, String name, int sortOrder) {
		this.store = store;
		this.name = name;
		this.sortOrder = sortOrder;
	}

	public void update(String name, int sortOrder) {
		this.name = name;
		this.sortOrder = sortOrder;
	}

	public Long getId() {
		return id;
	}

	public Store getStore() {
		return store;
	}

	public String getName() {
		return name;
	}

	public int getSortOrder() {
		return sortOrder;
	}
}

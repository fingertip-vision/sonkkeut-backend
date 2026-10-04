package com.sonkkeut.backend.store;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.sonkkeut.backend.menu.MenuItem;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** F-14 매장. code는 키오스크 옆 스티커·QR에 적는 6자리 공개 코드이고, 앱과 점주 화면 모두 이 코드로 매장을 찾는다. */
@Entity
@Table(name = "stores")
public class Store {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 12)
	private String code;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(length = 200)
	private String address;

	private Double lat;

	private Double lng;

	@Column(length = 80)
	private String kioskVendor;

	// 점주 키 원문은 매장을 만들 때 한 번만 보여 주고, 서버에는 해시만 남긴다.
	@Column(nullable = false, length = 128)
	private String ownerKeyHash;

	// 앱이 캐시한 메뉴가 최신인지 비교하는 값. 메뉴를 저장할 때마다 올린다.
	@Column(nullable = false)
	private int menuVersion;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@OneToMany(mappedBy = "store", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sort")
	private List<MenuItem> items = new ArrayList<>();

	protected Store() {
	}

	public Store(String code, String ownerKeyHash, String name, String address, Double lat, Double lng,
			String kioskVendor) {
		this.code = code;
		this.ownerKeyHash = ownerKeyHash;
		this.name = name;
		this.address = address;
		this.lat = lat;
		this.lng = lng;
		this.kioskVendor = kioskVendor;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public void replaceItems(List<MenuItem> newItems) {
		items.clear();
		items.addAll(newItems);
		menuVersion++;
		touch();
	}

	void setName(String name) {
		this.name = name;
		touch();
	}

	void setAddress(String address) {
		this.address = address;
		touch();
	}

	void setLat(Double lat) {
		this.lat = lat;
		touch();
	}

	void setLng(Double lng) {
		this.lng = lng;
		touch();
	}

	void setKioskVendor(String kioskVendor) {
		this.kioskVendor = kioskVendor;
		touch();
	}

	private void touch() {
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getAddress() {
		return address;
	}

	public Double getLat() {
		return lat;
	}

	public Double getLng() {
		return lng;
	}

	public String getKioskVendor() {
		return kioskVendor;
	}

	public String getOwnerKeyHash() {
		return ownerKeyHash;
	}

	public int getMenuVersion() {
		return menuVersion;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public List<MenuItem> getItems() {
		return items;
	}
}

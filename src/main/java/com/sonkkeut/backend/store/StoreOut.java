package com.sonkkeut.backend.store;

import java.time.Instant;

public record StoreOut(String code, String name, String address, Double lat, Double lng, String kioskVendor,
		int menuVersion, Instant updatedAt) {

	static StoreOut from(Store s) {
		return new StoreOut(s.getCode(), s.getName(), s.getAddress(), s.getLat(), s.getLng(), s.getKioskVendor(),
				s.getMenuVersion(), s.getUpdatedAt());
	}
}

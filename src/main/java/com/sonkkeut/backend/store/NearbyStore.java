package com.sonkkeut.backend.store;

import java.time.Instant;

public record NearbyStore(String code, String name, String address, Double lat, Double lng, String kioskVendor,
		int menuVersion, Instant updatedAt, double distanceM) {

	static NearbyStore from(Store s, double distanceM) {
		return new NearbyStore(s.getCode(), s.getName(), s.getAddress(), s.getLat(), s.getLng(), s.getKioskVendor(),
				s.getMenuVersion(), s.getUpdatedAt(), distanceM);
	}
}

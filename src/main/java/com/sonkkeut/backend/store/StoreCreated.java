package com.sonkkeut.backend.store;

import java.time.Instant;

/** 매장 등록 응답. owner_key는 이 응답에서 한 번만 보여 주므로 점주가 따로 적어 두어야 한다. */
public record StoreCreated(String code, String name, String address, Double lat, Double lng, String kioskVendor,
		int menuVersion, Instant updatedAt, String ownerKey) {

	static StoreCreated from(Store s, String ownerKey) {
		return new StoreCreated(s.getCode(), s.getName(), s.getAddress(), s.getLat(), s.getLng(), s.getKioskVendor(),
				s.getMenuVersion(), s.getUpdatedAt(), ownerKey);
	}
}

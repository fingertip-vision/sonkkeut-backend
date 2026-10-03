package com.sonkkeut.backend.store;

public record StoreResponse(Long id, String name, String storeCode, int dictionaryVersion) {

	static StoreResponse from(Store store) {
		return new StoreResponse(store.getId(), store.getName(), store.getStoreCode(), store.getDictionaryVersion());
	}
}

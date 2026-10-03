package com.sonkkeut.backend.store;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

	Optional<Store> findByStoreCode(String storeCode);

	boolean existsByStoreCode(String storeCode);
}

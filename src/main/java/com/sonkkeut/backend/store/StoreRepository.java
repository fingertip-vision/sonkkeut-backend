package com.sonkkeut.backend.store;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

	Optional<Store> findByCode(String code);

	boolean existsByCode(String code);

	List<Store> findAllByOrderByIdAsc();

	List<Store> findByLatBetweenAndLngBetween(double latFrom, double latTo, double lngFrom, double lngTo);
}

package com.sonkkeut.backend.menu;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sonkkeut.backend.store.Store;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

	List<MenuCategory> findByStoreOrderBySortOrderAscIdAsc(Store store);
}

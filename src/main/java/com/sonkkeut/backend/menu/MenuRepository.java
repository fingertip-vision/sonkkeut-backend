package com.sonkkeut.backend.menu;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sonkkeut.backend.store.Store;

public interface MenuRepository extends JpaRepository<Menu, Long> {

	List<Menu> findByCategoryStoreOrderBySortOrderAscIdAsc(Store store);

	boolean existsByCategory(MenuCategory category);
}

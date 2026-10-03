package com.sonkkeut.backend.menu;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/** F-14 메뉴 한 개. 이름·가격은 F-04 문자 인식 보정에, 별칭·옵션은 F-06 주문 이해에 쓰인다. */
@Entity
@Table(name = "menu")
public class Menu {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id")
	private MenuCategory category;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false)
	private int price;

	@Column(nullable = false)
	private boolean soldOut;

	@Column(nullable = false)
	private int sortOrder;

	// "아아"처럼 사용자가 실제로 말하는 이름. 화면에는 없어서 사전으로만 보정할 수 있다.
	@ElementCollection
	@CollectionTable(name = "menu_alias", joinColumns = @JoinColumn(name = "menu_id"))
	@OrderColumn(name = "position")
	@Column(name = "alias", nullable = false, length = 100)
	private List<String> aliases = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "menu_option", joinColumns = @JoinColumn(name = "menu_id"))
	@OrderColumn(name = "position")
	private List<MenuOption> options = new ArrayList<>();

	protected Menu() {
	}

	public Menu(MenuCategory category, String name, int price, boolean soldOut, int sortOrder, List<String> aliases,
			List<MenuOption> options) {
		update(category, name, price, soldOut, sortOrder, aliases, options);
	}

	public void update(MenuCategory category, String name, int price, boolean soldOut, int sortOrder,
			List<String> aliases, List<MenuOption> options) {
		this.category = category;
		this.name = name;
		this.price = price;
		this.soldOut = soldOut;
		this.sortOrder = sortOrder;
		this.aliases.clear();
		this.aliases.addAll(aliases);
		this.options.clear();
		this.options.addAll(options);
	}

	public Long getId() {
		return id;
	}

	public MenuCategory getCategory() {
		return category;
	}

	public String getName() {
		return name;
	}

	public int getPrice() {
		return price;
	}

	public boolean isSoldOut() {
		return soldOut;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public List<String> getAliases() {
		return aliases;
	}

	public List<MenuOption> getOptions() {
		return options;
	}
}

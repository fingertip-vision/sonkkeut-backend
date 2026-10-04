package com.sonkkeut.backend.menu;

import java.util.List;

import com.sonkkeut.backend.common.JsonColumnConverter;
import com.sonkkeut.backend.store.Store;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import tools.jackson.core.type.TypeReference;

/** F-14 메뉴 한 개. aliases는 OCR·음성 인식이 틀리기 쉬운 다른 이름(예: 아아 → 아이스 아메리카노)이다. */
@Entity
@Table(name = "menu_items")
public class MenuItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "store_id")
	private Store store;

	@Column(nullable = false, length = 40)
	private String category;

	@Column(nullable = false, length = 80)
	private String name;

	private Integer price;

	@Convert(converter = AliasesConverter.class)
	@Column(nullable = false, columnDefinition = "text")
	private List<String> aliases;

	@Convert(converter = OptionsConverter.class)
	@Column(nullable = false, columnDefinition = "text")
	private List<OptionGroup> options;

	@Column(nullable = false)
	private boolean soldOut;

	@Column(nullable = false)
	private int sort;

	protected MenuItem() {
	}

	public MenuItem(Store store, MenuItemIn in, int sort) {
		this.store = store;
		this.category = in.category();
		this.name = in.name();
		this.price = in.price();
		this.aliases = in.aliases();
		this.options = in.options();
		this.soldOut = in.soldOut();
		this.sort = sort;
	}

	public Long getId() {
		return id;
	}

	public String getCategory() {
		return category;
	}

	public String getName() {
		return name;
	}

	public Integer getPrice() {
		return price;
	}

	public List<String> getAliases() {
		return aliases;
	}

	public List<OptionGroup> getOptions() {
		return options;
	}

	public boolean isSoldOut() {
		return soldOut;
	}

	@Converter
	static class AliasesConverter extends JsonColumnConverter<List<String>> {
		AliasesConverter() {
			super(new TypeReference<>() {
			});
		}
	}

	@Converter
	static class OptionsConverter extends JsonColumnConverter<List<OptionGroup>> {
		OptionsConverter() {
			super(new TypeReference<>() {
			});
		}
	}
}

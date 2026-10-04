package com.sonkkeut.backend.store;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonkkeut.backend.common.ApiException;
import com.sonkkeut.backend.common.ValidationError;
import com.sonkkeut.backend.common.ValidationException;

import tools.jackson.databind.JsonNode;

@Service
public class StoreService {

	// 코드를 스티커로 붙이거나 말로 전할 때 헷갈리는 0·O, 1·I는 뺀다.
	private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int NEARBY_LIMIT = 20;
	private static final double EARTH_RADIUS_M = 6_371_000;

	private final StoreRepository storeRepository;
	private final StoreAccess storeAccess;
	private final SecureRandom random = new SecureRandom();

	public StoreService(StoreRepository storeRepository, StoreAccess storeAccess) {
		this.storeRepository = storeRepository;
		this.storeAccess = storeAccess;
	}

	public Store find(String code) {
		return storeRepository.findByCode(code.toUpperCase(Locale.ROOT))
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "매장을 찾을 수 없습니다"));
	}

	@Transactional
	public StoreCreated create(StoreCreate body) {
		String ownerKey = storeAccess.newOwnerKey();
		Store store = storeRepository.save(new Store(newCode(), StoreAccess.hash(ownerKey), body.name(),
				body.address(), body.lat(), body.lng(), body.kioskVendor()));
		return StoreCreated.from(store, ownerKey);
	}

	@Transactional(readOnly = true)
	public StoreOut get(String code) {
		return StoreOut.from(find(code));
	}

	@Transactional(readOnly = true)
	public List<StoreOut> list(String adminKey) {
		storeAccess.requireAdmin(adminKey, "운영자 키가 필요합니다");
		return storeRepository.findAllByOrderByIdAsc().stream().map(StoreOut::from).toList();
	}

	/** 앱이 현재 위치 근처 매장을 찾을 때 쓴다. 받은 위치는 거리 계산에만 쓰고 저장하지 않는다. */
	@Transactional(readOnly = true)
	public List<NearbyStore> nearby(double lat, double lng, double radiusM) {
		// 위도 1도는 약 111km. 대략적인 사각형으로 먼저 거른 뒤 실제 거리로 다시 거른다.
		double d = radiusM / 111_000;
		return storeRepository.findByLatBetweenAndLngBetween(lat - d, lat + d, lng - d * 2, lng + d * 2).stream()
				.map(s -> NearbyStore.from(s, Math.round(haversineM(lat, lng, s.getLat(), s.getLng()) * 10) / 10.0))
				.filter(s -> s.distanceM() <= radiusM)
				.sorted(Comparator.comparingDouble(NearbyStore::distanceM))
				.limit(NEARBY_LIMIT)
				.toList();
	}

	/** 보낸 필드만 바꾼다. null을 보내면 그 값을 지운다. 점주 화면은 빈 본문으로 점주 키가 맞는지 확인한다. */
	@Transactional
	public StoreOut update(String code, JsonNode body, String ownerKey, String adminKey) {
		Store store = find(code);
		storeAccess.requireOwner(store, ownerKey, adminKey);
		List<ValidationError> errors = new ArrayList<>();
		if (body.has("name")) {
			String name = text(body, "name", 1, 80, false, errors);
			if (name != null) {
				store.setName(name);
			}
		}
		if (body.has("address")) {
			String address = text(body, "address", 0, 200, true, errors);
			if (errors.isEmpty()) {
				store.setAddress(address);
			}
		}
		if (body.has("kiosk_vendor")) {
			String vendor = text(body, "kiosk_vendor", 0, 80, true, errors);
			if (errors.isEmpty()) {
				store.setKioskVendor(vendor);
			}
		}
		if (body.has("lat")) {
			Double lat = number(body, "lat", 90, errors);
			if (errors.isEmpty()) {
				store.setLat(lat);
			}
		}
		if (body.has("lng")) {
			Double lng = number(body, "lng", 180, errors);
			if (errors.isEmpty()) {
				store.setLng(lng);
			}
		}
		if (!errors.isEmpty()) {
			throw new ValidationException(errors);
		}
		return StoreOut.from(store);
	}

	@Transactional
	public void delete(String code, String ownerKey, String adminKey) {
		Store store = find(code);
		storeAccess.requireOwner(store, ownerKey, adminKey);
		storeRepository.delete(store);
	}

	private String newCode() {
		for (int attempt = 0; attempt < 20; attempt++) {
			StringBuilder sb = new StringBuilder(6);
			for (int i = 0; i < 6; i++) {
				sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
			}
			if (!storeRepository.existsByCode(sb.toString())) {
				return sb.toString();
			}
		}
		throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "매장 코드를 만들지 못했습니다");
	}

	private static String text(JsonNode body, String field, int min, int max, boolean nullable,
			List<ValidationError> errors) {
		JsonNode node = body.get(field);
		if (node.isNull() && nullable) {
			return null;
		}
		if (!node.isString() || node.asString().length() < min || node.asString().length() > max) {
			errors.add(new ValidationError(List.of("body", field), min + "~" + max + "자 문자열이어야 합니다", "value_error"));
			return null;
		}
		return node.asString();
	}

	private static Double number(JsonNode body, String field, double limit, List<ValidationError> errors) {
		JsonNode node = body.get(field);
		if (node.isNull()) {
			return null;
		}
		if (!node.isNumber() || Math.abs(node.asDouble()) > limit) {
			errors.add(new ValidationError(List.of("body", field), "-" + limit + "~" + limit + " 사이 숫자여야 합니다",
					"value_error"));
			return null;
		}
		return node.asDouble();
	}

	private static double haversineM(double lat1, double lng1, double lat2, double lng2) {
		double p1 = Math.toRadians(lat1);
		double p2 = Math.toRadians(lat2);
		double dp = p2 - p1;
		double dl = Math.toRadians(lng2 - lng1);
		double a = Math.pow(Math.sin(dp / 2), 2) + Math.cos(p1) * Math.cos(p2) * Math.pow(Math.sin(dl / 2), 2);
		return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
	}
}

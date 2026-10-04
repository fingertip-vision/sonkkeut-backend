package com.sonkkeut.backend.store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.sonkkeut.backend.common.ApiException;

/**
 * 점주 키·운영자 키 확인. 계정 없이 매장마다 키 하나를 주는 방식이라, 점주는 그 매장만, 운영자(팀)는 모든 매장을 다룬다.
 * 키를 비교할 때 길이·내용에 따라 걸리는 시간이 달라지지 않게 MessageDigest.isEqual을 쓴다.
 */
@Component
public class StoreAccess {

	private final byte[] adminKey;
	private final SecureRandom random = new SecureRandom();

	public StoreAccess(@Value("${sonkkeut.admin-key}") String adminKey) {
		// 운영자 키를 설정하지 않으면 운영자 API는 아무도 쓸 수 없다.
		this.adminKey = adminKey.isEmpty() ? null : adminKey.getBytes(StandardCharsets.UTF_8);
	}

	public boolean isAdmin(String key) {
		return adminKey != null && key != null && MessageDigest.isEqual(adminKey, key.getBytes(StandardCharsets.UTF_8));
	}

	public void requireAdmin(String key, String detail) {
		if (!isAdmin(key)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, detail);
		}
	}

	public void requireOwner(Store store, String ownerKey, String adminKey) {
		if (isAdmin(adminKey)) {
			return;
		}
		if (ownerKey == null || !MessageDigest.isEqual(hash(ownerKey).getBytes(StandardCharsets.UTF_8),
				store.getOwnerKeyHash().getBytes(StandardCharsets.UTF_8))) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "점주 키가 맞지 않습니다");
		}
	}

	public String newOwnerKey() {
		byte[] bytes = new byte[18];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String hash(String key) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}

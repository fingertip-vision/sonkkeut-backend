package com.sonkkeut.backend.store;

import java.security.SecureRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sonkkeut.backend.common.NotFoundException;

@Service
public class StoreService {

	// 코드를 말이나 글로 전달할 때 헷갈리는 0·O, 1·I는 뺀다.
	private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int CODE_LENGTH = 6;

	private final StoreRepository storeRepository;
	private final SecureRandom random = new SecureRandom();

	public StoreService(StoreRepository storeRepository) {
		this.storeRepository = storeRepository;
	}

	@Transactional
	public StoreResponse create(StoreRequest request) {
		return StoreResponse.from(storeRepository.save(new Store(request.name(), newStoreCode())));
	}

	@Transactional(readOnly = true)
	public StoreResponse get(Long storeId) {
		return StoreResponse.from(find(storeId));
	}

	public Store find(Long storeId) {
		return storeRepository.findById(storeId).orElseThrow(() -> new NotFoundException("store not found"));
	}

	private String newStoreCode() {
		String code;
		do {
			StringBuilder sb = new StringBuilder(CODE_LENGTH);
			for (int i = 0; i < CODE_LENGTH; i++) {
				sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
			}
			code = sb.toString();
		} while (storeRepository.existsByStoreCode(code));
		return code;
	}
}

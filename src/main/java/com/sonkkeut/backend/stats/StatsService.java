package com.sonkkeut.backend.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.sonkkeut.backend.common.ApiException;
import com.sonkkeut.backend.store.Store;
import com.sonkkeut.backend.store.StoreAccess;
import com.sonkkeut.backend.store.StoreRepository;
import com.sonkkeut.backend.store.StoreService;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Service
public class StatsService {

	// 날짜별 집계는 팀이 보는 한국 날짜 기준으로 나눈다.
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	// 같은 내용이면 필드 순서와 관계없이 같은 해시가 나오게 정렬해서 직렬화한다.
	private static final JsonMapper CANONICAL = JsonMapper.builder()
			.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
			.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
			.build();

	private final UsageSessionRepository sessionRepository;
	private final UsageReceiptRepository receiptRepository;
	private final StoreRepository storeRepository;
	private final StoreService storeService;
	private final StoreAccess storeAccess;
	private final StatsRateLimiter rateLimiter;
	private final TransactionTemplate transactionTemplate;
	private final EntityManager entityManager;

	public StatsService(UsageSessionRepository sessionRepository, UsageReceiptRepository receiptRepository,
			StoreRepository storeRepository, StoreService storeService, StoreAccess storeAccess,
			StatsRateLimiter rateLimiter, TransactionTemplate transactionTemplate, EntityManager entityManager) {
		this.sessionRepository = sessionRepository;
		this.receiptRepository = receiptRepository;
		this.storeRepository = storeRepository;
		this.storeService = storeService;
		this.storeAccess = storeAccess;
		this.rateLimiter = rateLimiter;
		this.transactionTemplate = transactionTemplate;
		this.entityManager = entityManager;
	}

	/** 저장했으면 true, 이미 받은 기록의 재전송이면 false. */
	public boolean record(SessionIn body, String ip) {
		rateLimiter.check(ip);
		String payloadHash = hash(body);
		if (body.eventId() != null) {
			UsageReceipt receipt = receiptRepository.findById(body.eventId()).orElse(null);
			if (receipt != null) {
				requireSamePayload(receipt, payloadHash, "다른 통계에 같은 이벤트 ID를 사용할 수 없습니다");
				return false;
			}
		}
		// 모르는 매장 코드는 버리고 기록만 남긴다. 통계 때문에 앱이 오류를 처리할 일은 없어야 한다.
		String code = body.storeCode() == null ? null : body.storeCode().toUpperCase(Locale.ROOT);
		if (code != null && !storeRepository.existsByCode(code)) {
			code = null;
		}
		String storeCode = code;
		try {
			transactionTemplate.executeWithoutResult(status -> {
				if (body.eventId() != null) {
					// save()는 키가 정해진 엔티티를 병합(merge)해 버려 중복을 못 잡으므로 persist로 넣는다.
					entityManager.persist(new UsageReceipt(body.eventId(), payloadHash));
				}
				entityManager.persist(new UsageSession(storeCode, body));
			});
		} catch (DataIntegrityViolationException | ConstraintViolationException e) {
			// 같은 event_id가 동시에 두 번 들어와 먼저 커밋된 쪽이 있는 경우. IDENTITY 키인 기록을 넣을 때 앞선 insert가
			// 바로 실행되면서 Spring 예외로 바뀌기 전의 Hibernate 예외가 올라올 수 있어 둘 다 받는다.
			UsageReceipt receipt = receiptRepository.findById(body.eventId())
					.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "통계 기록 충돌"));
			requireSamePayload(receipt, payloadHash, "통계 기록 충돌");
			return false;
		}
		return true;
	}

	/** 매장 코드를 주면 그 매장(점주 키), 안 주면 전체(운영자 키). */
	public Summary summary(String storeCode, int days, String ownerKey, String adminKey) {
		Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
		List<UsageSession> rows;
		if (storeCode != null) {
			Store store = storeService.find(storeCode);
			storeAccess.requireOwner(store, ownerKey, adminKey);
			rows = sessionRepository.findByStoreCodeAndCreatedAtGreaterThanEqualOrderByIdAsc(store.getCode(), since);
		} else {
			storeAccess.requireAdmin(adminKey, "전체 통계는 운영자 키가 필요합니다");
			rows = sessionRepository.findByCreatedAtGreaterThanEqualOrderByIdAsc(since);
		}
		return summarize(rows);
	}

	private static Summary summarize(List<UsageSession> rows) {
		List<StepIn> steps = rows.stream().flatMap(r -> r.getSteps().stream()).toList();
		Map<String, List<StepIn>> byScreen = new LinkedHashMap<>();
		Map<String, Integer> fails = new LinkedHashMap<>();
		for (StepIn step : steps) {
			byScreen.computeIfAbsent(step.screenType(), k -> new ArrayList<>()).add(step);
			if (!step.result().equals("success")) {
				fails.merge(step.failReason() != null ? step.failReason() : step.result(), 1, Integer::sum);
			}
		}
		Map<String, Summary.ScreenStat> screenStats = new LinkedHashMap<>();
		byScreen.forEach((screen, list) -> screenStats.put(screen, new Summary.ScreenStat(list.size(),
				round(successCount(list) / (double) list.size(), 3), mean(list.stream().map(StepIn::reachS).toList()))));

		// 많이 나온 순서로 10개. 횟수가 같으면 먼저 나온 원인이 앞선다.
		Map<String, Integer> topFails = new LinkedHashMap<>();
		fails.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(10)
				.forEach(e -> topFails.put(e.getKey(), e.getValue()));

		Map<String, int[]> daily = new TreeMap<>();
		for (UsageSession r : rows) {
			int[] counts = daily.computeIfAbsent(r.getCreatedAt().atZone(ZONE).toLocalDate().toString(), k -> new int[2]);
			counts[0]++;
			counts[1] += r.isCompleted() ? 1 : 0;
		}
		long completed = rows.stream().filter(UsageSession::isCompleted).count();
		return new Summary(rows.size(),
				rows.isEmpty() ? null : round(completed / (double) rows.size(), 3),
				steps.size(),
				steps.isEmpty() ? null : round(successCount(steps) / (double) steps.size(), 3),
				mean(steps.stream().map(StepIn::reachS).toList()),
				mean(rows.stream().map(UsageSession::getDurationS).toList()),
				screenStats, topFails,
				daily.entrySet().stream().map(e -> new Summary.Daily(e.getKey(), e.getValue()[0], e.getValue()[1]))
						.toList());
	}

	private static long successCount(List<StepIn> steps) {
		return steps.stream().filter(s -> s.result().equals("success")).count();
	}

	private static Double mean(List<Double> values) {
		List<Double> present = values.stream().filter(Objects::nonNull).toList();
		if (present.isEmpty()) {
			return null;
		}
		return round(present.stream().mapToDouble(Double::doubleValue).sum() / present.size(), 2);
	}

	private static double round(double value, int scale) {
		return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_EVEN).doubleValue();
	}

	private static void requireSamePayload(UsageReceipt receipt, String payloadHash, String detail) {
		if (!receipt.getPayloadHash().equals(payloadHash)) {
			throw new ApiException(HttpStatus.CONFLICT, detail);
		}
	}

	private static String hash(SessionIn body) {
		try {
			byte[] json = CANONICAL.writeValueAsBytes(body.withoutEventId());
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}

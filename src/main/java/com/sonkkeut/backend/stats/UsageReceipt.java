package com.sonkkeut.backend.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 이미 받은 event_id. 기본 키 충돌로 동시에 들어온 재전송도 한 번만 저장되게 한다. 기기 식별자가 아니다. */
@Entity
@Table(name = "usage_receipts")
public class UsageReceipt {

	@Id
	@Column(length = 80)
	private String eventId;

	@Column(nullable = false, length = 64)
	private String payloadHash;

	protected UsageReceipt() {
	}

	UsageReceipt(String eventId, String payloadHash) {
		this.eventId = eventId;
		this.payloadHash = payloadHash;
	}

	String getPayloadHash() {
		return payloadHash;
	}
}

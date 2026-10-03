package com.sonkkeut.backend.stats;

/** 주문 한 번의 결과. COMPLETED는 결제 화면(S6) 도달, ABANDONED는 사용자가 그만둠, FAILED는 복구하지 못하고 끝남. */
public enum SessionResult {
	COMPLETED, ABANDONED, FAILED
}

package com.sonkkeut.backend.common;

import org.springframework.http.HttpStatus;

/** 상태 코드와 함께 응답할 요청 오류. 메시지는 그대로 응답 본문 detail에 들어간다. */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	public ApiException(HttpStatus status, String detail) {
		super(detail);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}

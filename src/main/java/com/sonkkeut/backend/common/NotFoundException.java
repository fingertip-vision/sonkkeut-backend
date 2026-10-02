package com.sonkkeut.backend.common;

/** 404로 응답할 요청 오류. 메시지는 그대로 응답 본문 error에 들어간다. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}

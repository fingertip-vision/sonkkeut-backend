package com.sonkkeut.backend.common;

/**
 * 모든 에러 응답 본문. 앱과 점주 화면이 PR #1(FastAPI)과 같은 방식으로 처리할 수 있게 {"detail": ...}로 맞춘다.
 * detail은 문자열이거나, 검증 오류일 때 {@link ValidationError} 목록이다.
 */
public record ApiError(Object detail) {
}

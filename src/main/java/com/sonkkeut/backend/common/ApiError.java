package com.sonkkeut.backend.common;

/** 모든 에러 응답 본문. 앱과 점주 화면이 같은 방식으로 처리할 수 있게 {"error": "..."} 하나로 통일한다. */
public record ApiError(String error) {
}

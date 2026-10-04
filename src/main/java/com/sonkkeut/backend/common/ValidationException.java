package com.sonkkeut.backend.common;

import java.util.List;

/** Bean Validation을 거치지 않고 직접 검사한 요청의 오류. 422와 검증 오류 목록으로 응답한다. */
public class ValidationException extends RuntimeException {

	private final List<ValidationError> errors;

	public ValidationException(List<ValidationError> errors) {
		this.errors = errors;
	}

	public List<ValidationError> getErrors() {
		return errors;
	}
}

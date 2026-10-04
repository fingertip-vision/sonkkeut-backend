package com.sonkkeut.backend.common;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * 요청 오류를 PR #1(FastAPI)과 같은 형식으로 통일한다. 앱이 이미 그 형식으로 붙어 봤기 때문에
 * 형식이 틀린 요청은 400이 아니라 422로, 본문은 {"detail": [...]}로 돌려준다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> api(ApiException e) {
		return ResponseEntity.status(e.getStatus()).body(new ApiError(e.getMessage()));
	}

	@ExceptionHandler(ValidationException.class)
	@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
	ApiError invalid(ValidationException e) {
		return new ApiError(e.getErrors());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
	ApiError invalid(MethodArgumentNotValidException e) {
		List<ValidationError> errors = new ArrayList<>();
		for (FieldError error : e.getBindingResult().getFieldErrors()) {
			errors.add(new ValidationError(bodyLoc(error.getField()), error.getDefaultMessage(), "value_error"));
		}
		e.getBindingResult().getGlobalErrors().forEach(error ->
				errors.add(new ValidationError(List.of("body"), error.getDefaultMessage(), "value_error")));
		return new ApiError(errors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
	ApiError unreadable(HttpMessageNotReadableException e) {
		// 통계에 영상·음성·위치 같은 필드를 실어 보내면 여기서 막힌다. 어떤 필드였는지만 알려 준다.
		if (e.getMostSpecificCause() instanceof UnrecognizedPropertyException unknown) {
			return new ApiError(List.of(new ValidationError(List.of("body", unknown.getPropertyName()),
					"정의되지 않은 필드입니다", "extra_forbidden")));
		}
		// 앱이 보낸 형식이 바뀌었을 때 원인을 찾을 수 있게 서버 로그에는 남기고, 응답에는 내부 정보를 싣지 않는다.
		log.warn("요청 본문을 읽지 못함: {}", e.getMostSpecificCause().getMessage());
		return new ApiError(List.of(new ValidationError(List.of("body"), "요청 본문을 읽을 수 없습니다", "json_invalid")));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
	ApiError missing(MissingServletRequestParameterException e) {
		return new ApiError(List.of(new ValidationError(List.of("query", e.getParameterName()), "필수 값입니다",
				"missing")));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
	ApiError mismatch(MethodArgumentTypeMismatchException e) {
		return new ApiError(List.of(new ValidationError(List.of("query", e.getName()), "형식이 맞지 않습니다",
				"type_error")));
	}

	// "items[0].soldOut" → ["body", "items", 0, "sold_out"]. 요청 JSON과 같은 snake_case 이름으로 알려 준다.
	private static List<Object> bodyLoc(String field) {
		List<Object> loc = new ArrayList<>();
		loc.add("body");
		for (String part : field.split("\\.")) {
			int bracket = part.indexOf('[');
			if (bracket < 0) {
				loc.add(snake(part));
				continue;
			}
			loc.add(snake(part.substring(0, bracket)));
			loc.add(Integer.parseInt(part.substring(bracket + 1, part.length() - 1)));
		}
		return loc;
	}

	private static String snake(String name) {
		return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
	}
}

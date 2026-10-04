package com.sonkkeut.backend.common;

import java.util.List;

/** 검증 오류 한 건. 점주 화면(owner.js)이 loc 끝 두 칸과 msg를 이어 붙여 보여 준다. */
public record ValidationError(List<Object> loc, String msg, String type) {
}

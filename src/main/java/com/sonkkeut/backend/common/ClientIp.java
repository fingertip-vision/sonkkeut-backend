package com.sonkkeut.backend.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청을 보낸 쪽 IP. 서버는 인바운드 포트 없이 Cloudflare 터널로만 열려 있고, Cloudflare는 CF-Connecting-IP를
 * 실제 접속 주소로 덮어써서 보낸다. X-Forwarded-For 첫 값은 보내는 쪽이 마음대로 넣을 수 있어 쓰지 않는다.
 * 헤더가 없으면(로컬 실행) 접속 주소를 쓴다.
 */
public final class ClientIp {

	static final String HEADER = "CF-Connecting-IP";

	private ClientIp() {
	}

	public static String of(HttpServletRequest request) {
		String ip = request.getHeader(HEADER);
		return ip == null || ip.isBlank() ? request.getRemoteAddr() : ip.strip();
	}
}

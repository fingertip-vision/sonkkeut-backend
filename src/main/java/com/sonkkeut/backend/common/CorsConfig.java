package com.sonkkeut.backend.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프론트(React)가 브라우저에서 도는지 앱으로 도는지 아직 정해지지 않아, 브라우저에서 호출해도 막히지 않게 열어 둔다.
 * 쿠키를 쓰지 않으므로 allowCredentials는 끈 채로 둔다. 주소가 정해지면 CORS_ALLOWED_ORIGINS로 좁힌다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	private final String[] allowedOrigins;

	public CorsConfig(@Value("${cors.allowed-origins}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		// 기본값은 GET·HEAD·POST뿐이라 메뉴 수정·삭제에 쓰는 PUT·DELETE를 더한다.
		registry.addMapping("/api/**")
				.allowedOriginPatterns(allowedOrigins)
				.allowedMethods("GET", "HEAD", "POST", "PUT", "DELETE")
				// 앱이 사전 버전을 비교하려면 응답의 ETag를 읽을 수 있어야 한다.
				.exposedHeaders("ETag");
	}
}

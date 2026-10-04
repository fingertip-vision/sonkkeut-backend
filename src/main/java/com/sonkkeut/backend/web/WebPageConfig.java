package com.sonkkeut.backend.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * PR #1에서 옮긴 점주 메뉴 등록·사용 통계·시연 키오스크 화면. 화면 파일이 /static/... 경로로 서로를 부르므로
 * 같은 경로에 그대로 내보낸다. 화면은 모두 같은 서버의 API만 호출한다.
 */
@Configuration
public class WebPageConfig implements WebMvcConfigurer {

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/static/**").addResourceLocations("classpath:/web/");
	}

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addRedirectViewController("/", "/owner");
		registry.addViewController("/owner").setViewName("forward:/static/owner.html");
		registry.addViewController("/dashboard").setViewName("forward:/static/dashboard.html");
		registry.addViewController("/kiosk").setViewName("forward:/static/kiosk.html");
		registry.addViewController("/simulation").setViewName("forward:/static/simulation.html");
	}
}

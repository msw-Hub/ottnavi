package com.ottnavi.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

/**
 * 요청이 /api 아래인지 판정한다. Security 체인보다 앞선 필터가 쓰므로 인코딩·정규화를 직접 처리해야 한다.
 * getRequestURI()는 퍼센트 인코딩을 풀지 않은 원본이라 /%61pi/... 같은 경로가 /api로 판정되지 않지만,
 * Spring MVC는 디코딩한 경로로 매칭하므로 검사를 우회할 수 있다(CWE-647). 그래서 MVC와 같은 방식으로
 * 디코딩하고 세미콜론 매개변수(/api;x=1)와 중복 슬래시(//api)를 정리한 경로로 비교한다.
 */
final class ApiRequestPath {

	private static final String API_PATH_PREFIX = "/api/";

	private ApiRequestPath() {
	}

	/** 디코딩·정규화한 경로가 /api/ 로 시작하면 true */
	static boolean isApi(HttpServletRequest request) {
		return UrlPathHelper.defaultInstance.getPathWithinApplication(request).startsWith(API_PATH_PREFIX);
	}
}

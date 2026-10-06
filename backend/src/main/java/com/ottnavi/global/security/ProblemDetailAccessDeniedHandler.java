package com.ottnavi.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 권한 부족(403)을 GlobalExceptionHandler에 넘겨 다른 오류와 같은 ProblemDetail 형식으로 응답한다.
 * 위임 이유는 ProblemDetailAuthenticationEntryPoint와 같다.
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

	private final HandlerExceptionResolver handlerExceptionResolver;

	// Lombok 생성자는 @Qualifier를 옮기지 않으므로 직접 작성한다
	public ProblemDetailAccessDeniedHandler(
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
		this.handlerExceptionResolver = handlerExceptionResolver;
	}

	/** 접근 거부 예외를 MVC 예외 처리로 넘긴다 */
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) {
		handlerExceptionResolver.resolveException(request, response, null, accessDeniedException);
	}
}

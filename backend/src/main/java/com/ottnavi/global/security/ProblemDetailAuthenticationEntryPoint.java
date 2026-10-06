package com.ottnavi.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 인증 실패(401)를 GlobalExceptionHandler에 넘겨 다른 오류와 같은 ProblemDetail 형식으로 응답한다.
 * 보안 필터는 DispatcherServlet 앞에서 동작해 @RestControllerAdvice가 직접 받지 못하므로 HandlerExceptionResolver로 위임한다.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final HandlerExceptionResolver handlerExceptionResolver;

	// Lombok 생성자는 @Qualifier를 옮기지 않으므로 직접 작성한다
	public ProblemDetailAuthenticationEntryPoint(
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
		this.handlerExceptionResolver = handlerExceptionResolver;
	}

	/** 인증 예외를 MVC 예외 처리로 넘긴다 */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) {
		handlerExceptionResolver.resolveException(request, response, null, authException);
	}
}

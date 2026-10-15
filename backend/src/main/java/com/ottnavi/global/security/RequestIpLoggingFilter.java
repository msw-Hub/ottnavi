package com.ottnavi.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /api 진입 요청의 방문자 IP(remoteAddr)를 debug 로그로 남긴다(TECH 4절). IP는 개인정보 성격이 있어
 * 기본 로그 레벨(info)에서는 남기지 않고, 프록시 설정을 점검할 때만 logging.level로 debug를 켠다.
 * Boot의 ForwardedHeaderFilter(order MIN)가 먼저 돌아 X-Forwarded-For 첫 값이 remoteAddr에 반영된다.
 * x-real-ip는 Vercel의 IP라 방문자 IP가 아니므로 남기지 않는다(TECH 4절 Task 021 실측).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // 오리진 비밀 헤더 검사(+20)보다 먼저 남겨 거부된 요청도 기록한다
public class RequestIpLoggingFilter extends OncePerRequestFilter {

	/** /api 밖의 요청은 기록하지 않는다(경로 판정은 오리진 필터와 같은 ApiRequestPath를 쓴다) */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !ApiRequestPath.isApi(request);
	}

	/** 방문자 IP를 기록하고 다음 필터로 넘긴다 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		log.debug("진입 요청: method={}, uri={}, remoteAddr={}",
				request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
		filterChain.doFilter(request, response);
	}
}

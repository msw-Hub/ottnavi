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
 * /api 진입 요청의 IP 관련 값을 로그로 남긴다(TECH 4절). 배포 후 Vercel이 실제로 보내는 값을 확인하려는 임시 로그이며
 * IP는 개인정보 성격이 있으므로 Task 049에서 레벨·항목을 정리한다.
 * Boot의 ForwardedHeaderFilter(order MIN)가 먼저 돌며 X-Forwarded-* 원본을 요청에서 숨기므로,
 * x-forwarded-for는 반영된 remoteAddr(첫 값), x-forwarded-host는 반영된 serverName으로 남긴다. x-real-ip는 숨김 대상이 아니라 원본이다.
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

	/** 요청 IP 관련 값을 기록하고 다음 필터로 넘긴다 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		log.info("진입 요청: method={}, uri={}, x-real-ip={}, remoteAddr={}, serverName={}",
				request.getMethod(), request.getRequestURI(), request.getHeader("x-real-ip"),
				request.getRemoteAddr(), request.getServerName());
		filterChain.doFilter(request, response);
	}
}

package com.ottnavi.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서비스가 "오늘"과 "지금"을 읽는 시계. 한국 시간(KST) 기준이고, 시험에서 고정 시계를 주입할 수 있게 빈으로 둔다.
 * 예: LocalDate.now(clock) → 한국 날짜, Instant.now(clock) → 현재 시각
 */
@Configuration
public class ClockConfig {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Bean
	public Clock clock() {
		return Clock.system(KST);
	}
}

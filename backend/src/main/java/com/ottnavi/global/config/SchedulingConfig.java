package com.ottnavi.global.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러 설정. 서버가 여러 대여도 같은 작업이 동시에 한 곳에서만 돌도록 ShedLock(shedlock 표, V7)으로 잠근다.
 * defaultLockAtMostFor는 작업이 비정상 종료돼도 락이 이 시간 뒤에는 풀리게 하는 안전장치다(알림 발송 임대 5분보다 짧게 둔다, TECH 5절).
 * usingDbTime은 서버 시계가 서로 달라도 DB 시계 하나로 락 만료를 판단하게 한다.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "4m")
public class SchedulingConfig {

	@Bean
	public LockProvider lockProvider(DataSource dataSource) {
		return new JdbcTemplateLockProvider(
				JdbcTemplateLockProvider.Configuration.builder()
						.withJdbcTemplate(new JdbcTemplate(dataSource))
						.usingDbTime()
						.build());
	}
}

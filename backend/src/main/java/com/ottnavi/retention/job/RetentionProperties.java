package com.ottnavi.retention.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 데이터 정리 설정(app.retention.*, Task 083). 값은 application.yml에 있다.
 *
 * @param staleMonths 마지막 수집이 이 개월 수보다 오래된 작품을 TMDB에 다시 묻는다(기본 5, 약관 보관 기한 6개월보다 앞선다)
 * @param historyRetentionDays BATCH_* 이력과 플랜과 끊긴 calc_run의 보관 일수(기본 180)
 * @param maxTitlesPerRun 한 번 실행에 점검할 작품 수 상한. 가장 오래된 순으로 자르고 나머지는 다음 실행에서 처리한다(기본 500)
 */
@ConfigurationProperties(prefix = "app.retention")
public record RetentionProperties(int staleMonths, int historyRetentionDays, int maxTitlesPerRun) {

	public RetentionProperties {
		if (staleMonths < 1 || historyRetentionDays < 1 || maxTitlesPerRun < 1) {
			throw new IllegalArgumentException("정리 설정 값은 모두 1 이상이어야 한다");
		}
	}
}

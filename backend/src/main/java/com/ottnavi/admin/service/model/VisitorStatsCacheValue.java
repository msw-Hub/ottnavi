package com.ottnavi.admin.service.model;

import java.time.Instant;

/**
 * Redis 캐시(visitorStats)에 넣는 방문자 수. 성공한 결과만 담는다.
 * 시각은 OffsetDateTime이 아니라 Instant로 둔다(JSON을 되읽을 때 오프셋이 바뀌지 않게). 응답에서 KST 오프셋으로 바꾼다.
 *
 * @param fetchedAt GA에서 값을 받아 온 시각(캐시를 만든 시각)
 */
public record VisitorStatsCacheValue(long todayVisitorCount, long yesterdayVisitorCount, long last7DaysVisitorCount,
		long last30DaysVisitorCount, Instant fetchedAt) {
}

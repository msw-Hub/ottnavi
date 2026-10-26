package com.ottnavi.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 서비스별 독점 목록 캐시 설정(app.exclusive.cache.*, Task 078). 설정이 없어도 아래 기본값으로 동작한다.
 * 수집 Job이 성공으로 끝나면 새로 계산해 채우므로 TTL은 "수집이 오래 없을 때의 안전장치"다.
 *
 * @param ttl 독점 목록 캐시 유효 시간(기본 24시간)
 */
@ConfigurationProperties(prefix = "app.exclusive.cache")
public record ExclusiveCacheProperties(@DefaultValue("24h") Duration ttl) {
}

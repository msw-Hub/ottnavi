package com.ottnavi.global.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;

/**
 * 키별 토큰 버킷을 돌려주는 창구. 운영은 Redis에 버킷 상태를 두고 서버 여러 대가 함께 쓰며,
 * 테스트는 메모리 버킷으로 바꿔 끼워 Docker 없이 한도 동작을 검증한다.
 */
public interface RateLimitBucketProvider {

	/**
	 * 키에 대응하는 버킷을 돌려준다. 같은 키는 같은 버킷이고, 없으면 configuration으로 새로 만든다.
	 * 저장소 연결 문제가 있으면 RuntimeException을 던질 수 있다(호출하는 쪽이 fail-open으로 처리한다).
	 */
	Bucket getBucket(String key, BucketConfiguration configuration);
}

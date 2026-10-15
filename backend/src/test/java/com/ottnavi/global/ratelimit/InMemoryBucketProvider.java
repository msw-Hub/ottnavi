package com.ottnavi.global.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import java.util.HashMap;
import java.util.Map;

/** 테스트용 메모리 버킷 창구. Redis 없이 키별 버킷을 같은 객체로 돌려준다(Docker 불필요) */
class InMemoryBucketProvider implements RateLimitBucketProvider {

	private final Map<String, Bucket> buckets = new HashMap<>();
	int callCount; // getBucket 호출 횟수. 저장소를 건너뛰었는지 확인할 때 쓴다

	@Override
	public Bucket getBucket(String key, BucketConfiguration configuration) {
		callCount++;
		Bucket bucket = buckets.get(key);
		if (bucket == null) {
			bucket = Bucket.builder().addLimit(configuration.getBandwidths()[0]).build();
			buckets.put(key, bucket);
		}
		return bucket;
	}

	void clear() {
		buckets.clear();
		callCount = 0;
	}

	boolean hasKey(String key) {
		return buckets.containsKey(key);
	}
}

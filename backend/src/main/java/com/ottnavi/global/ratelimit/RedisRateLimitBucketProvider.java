package com.ottnavi.global.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Bucket4j의 Redis(Lettuce) 분산 버킷을 돌려주는 구현.
 * 버킷 상태는 Redis에 키 하나로 저장되고, 서버가 여러 대여도 같은 키는 같은 한도를 공유한다.
 *
 * 연결은 Spring Data Redis가 이미 만든 Lettuce 클라이언트(RedisClient)에서 얻는다.
 * 이 클라이언트는 spring.data.redis.* 의 호스트·비밀번호·SSL·DB·타임아웃·클라이언트 옵션을 그대로 갖고 있어
 * 운영(prod)의 접속 정보를 한 번 더 설정하지 않는다.
 * Bucket4j는 값을 byte[]로 읽고 써야 해서, 같은 클라이언트에서 byte[] 값 코덱의 연결을 따로 하나 연다.
 *
 * 연결은 처음 쓰는 순간에 만든다. 기동 때 만들면 Redis가 내려가 있을 때 앱이 뜨지 못해
 * "저장소 오류면 요청을 통과시킨다(fail-open)"는 원칙과 어긋나기 때문이다.
 */
@Slf4j
public class RedisRateLimitBucketProvider implements RateLimitBucketProvider, AutoCloseable {

	// 버킷이 가득 찬 뒤 키를 더 들고 있을 시간. 가득 찬 버킷은 새로 만든 것과 같아서 지워도 한도가 달라지지 않는다
	private static final Duration KEEP_AFTER_FULL = Duration.ofSeconds(10);
	private static final int MAX_CAS_RETRIES = 5; // 동시 갱신 충돌 시 재시도 상한. 넘으면 예외를 던져 fail-open으로 간다

	private final RedisConnectionFactory connectionFactory;
	private volatile ProxyManager<String> proxyManager;              // 처음 쓸 때 만든다
	private StatefulRedisConnection<String, byte[]> connection;      // close에서 닫는다

	public RedisRateLimitBucketProvider(RedisConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}

	@Override
	public Bucket getBucket(String key, BucketConfiguration configuration) {
		return getProxyManager().builder().build(key, configuration);
	}

	/** 처음 한 번만 Redis에 연결해 ProxyManager를 만든다. 실패하면 다음 호출에서 다시 시도한다 */
	private ProxyManager<String> getProxyManager() {
		ProxyManager<String> current = proxyManager;
		if (current != null) {
			return current;
		}
		synchronized (this) {
			if (proxyManager == null) {
				proxyManager = createProxyManager();
			}
			return proxyManager;
		}
	}

	private ProxyManager<String> createProxyManager() {
		// 1. Spring Data Redis가 쓰는 Lettuce 클라이언트를 꺼낸다(접속 정보가 이미 들어 있다)
		if (!(connectionFactory instanceof LettuceConnectionFactory lettuceFactory)) {
			throw new IllegalStateException("요청 제한은 Lettuce 연결 팩토리가 필요합니다: " + connectionFactory.getClass().getName());
		}
		AbstractRedisClient nativeClient = lettuceFactory.getRequiredNativeClient();
		if (!(nativeClient instanceof RedisClient redisClient)) {
			throw new IllegalStateException("요청 제한은 단일 Redis 클라이언트만 지원합니다: " + nativeClient.getClass().getName());
		}

		// 2. 키는 문자열, 값은 byte[]인 연결을 연다
		connection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

		// 3. Redis 키 TTL은 Bucket4j가 정한다(버킷이 가득 차는 시점 + KEEP_AFTER_FULL)
		ProxyManager<String> created = Bucket4jLettuce.casBasedBuilder(connection)
				.expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(KEEP_AFTER_FULL))
				.maxRetries(MAX_CAS_RETRIES)
				.build();
		log.info("요청 제한용 Redis 연결을 만들었습니다.");
		return created;
	}

	/** 앱 종료 때 요청 제한용 연결을 닫는다 */
	@Override
	public synchronized void close() {
		if (connection != null) {
			connection.close();
			connection = null;
		}
		proxyManager = null;
	}
}

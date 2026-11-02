package com.ottnavi.global.config;

import com.ottnavi.global.common.ExceptionRootCause;
import com.ottnavi.global.ratelimit.RedisRateLimitBucketProvider;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * 기동 직후 Redis 연결을 미리 만든다(예열). 첫 방문자 요청이 연결을 만드느라 느려지는 것을 줄인다.
 * 서버가 완전히 뜬 뒤 별도 데몬 스레드 하나에서 실행하고, 실패해도 앱은 계속 동작한다(fail-open 원칙 유지).
 * 하나라도 실패하면 retry-delay 뒤 실패한 쪽만 한 번 더 시도하고 끝낸다.
 * app.redis.warm-up.enabled=false면 이 빈이 만들어지지 않는다.
 * 로그에는 호스트·비밀번호가 섞일 수 있어 예외 메시지 없이 근본원인 클래스 이름만 남긴다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.redis.warm-up", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisWarmUp {

	static final String THREAD_NAME = "redis-warm-up";

	private final ObjectProvider<RedisRateLimitBucketProvider> rateLimitBucketProvider; // 요청 제한을 끄면 없다
	private final RedisConnectionFactory connectionFactory;
	private final Duration retryDelay;

	public RedisWarmUp(ObjectProvider<RedisRateLimitBucketProvider> rateLimitBucketProvider,
			RedisConnectionFactory connectionFactory, RedisClientProperties properties) {
		this.rateLimitBucketProvider = rateLimitBucketProvider;
		this.connectionFactory = connectionFactory;
		this.retryDelay = properties.warmUp().retryDelay();
	}

	/** 서버가 요청을 받을 준비를 마치면 예열을 백그라운드 데몬 스레드에서 시작한다. 기동·종료를 붙잡지 않는다 */
	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {
		Thread thread = new Thread(this::warmUp, THREAD_NAME);
		thread.setDaemon(true);
		thread.start();
	}

	/** 예열을 한 번 하고, 실패한 쪽이 있으면 retry-delay 뒤 그쪽만 한 번 더 한다. 예외는 밖으로 내보내지 않는다 */
	void warmUp() {
		// 1. 요청 제한용 연결(Bucket4j가 쓰는 별도 연결). 요청 제한을 끄면 제공자가 없어 할 일이 없다
		RedisRateLimitBucketProvider provider = rateLimitBucketProvider.getIfAvailable();
		boolean rateLimitFailed = false;
		if (provider != null) {
			rateLimitFailed = !runStep("예열", "요청 제한용 연결", provider::warmUp);
		}
		// 2. Spring 공유 연결(캐시·토큰 저장 등): 가벼운 PING 한 번
		boolean pingFailed = !runStep("예열", "공유 연결", this::ping);

		// 3. 모두 성공했으면 끝낸다
		if (!rateLimitFailed && !pingFailed) {
			return;
		}

		// 4. 잠시 기다린다. 인터럽트(종료 등)가 오면 조용히 끝낸다
		try {
			Thread.sleep(retryDelay.toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}

		// 5. 실패한 쪽만 한 번 더 시도하고, 또 실패해도 더 반복하지 않는다
		if (rateLimitFailed) {
			runStep("예열 재시도", "요청 제한용 연결", provider::warmUp);
		}
		if (pingFailed) {
			runStep("예열 재시도", "공유 연결", this::ping);
		}
	}

	private void ping() {
		try (RedisConnection connection = connectionFactory.getConnection()) {
			connection.ping();
		}
	}

	/** 한 단계를 실행하고 결과를 로그로 남긴다. 성공하면 true */
	private boolean runStep(String label, String name, Runnable step) {
		long startNanos = System.nanoTime();
		try {
			step.run();
			log.info("Redis {} 완료: 대상={}, 소요 ms={}", label, name, elapsedMs(startNanos)); // 이전: "{}을 마쳤다"(라벨이 "예열 재시도"이면 조사가 어긋났다)
			return true;
		} catch (RuntimeException e) {
			log.warn("Redis {}에 실패했다(앱은 계속 동작한다): 대상={}, 소요 ms={}, 근본원인={}",
					label, name, elapsedMs(startNanos), ExceptionRootCause.rootCauseName(e));
			return false;
		}
	}

	private static long elapsedMs(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}
}

package com.ottnavi.global.security.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.support.IntegrationTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * 실제 Redis(Testcontainers)에서 Refresh Token 교체·재사용 감지·폐기를 확인한다(Docker Desktop 필요).
 * 유예는 1초로 줄여 시간 경과 케이스를 짧게 확인한다.
 * 막으려는 사고: 같은 토큰 동시 요청이 둘 다 교체되는 일, 탈취된 직전 토큰이 계속 쓰이는 일, 폐기 뒤 토큰이 살아 있는 일.
 */
@TestPropertySource(properties = "app.jwt.refresh-reuse-grace=1s")
class RedisRefreshTokenStoreTest extends IntegrationTestSupport {

	private static final long GRACE_PASS_MILLIS = 1300;

	@Autowired
	private RefreshTokenStore store;

	@Autowired
	private StringRedisTemplate redis;

	@Test
	void 발급한_토큰을_회전하면_새_토큰이_나오고_옛_토큰은_직전_토큰이_된다() {
		// given
		String token = store.issue(101L);

		// when
		RefreshRotation rotation = store.rotate(token);

		// then
		assertThat(rotation.result()).isEqualTo(RefreshRotation.Result.ROTATED);
		assertThat(rotation.userId()).isEqualTo(101L);
		assertThat(rotation.newToken()).isNotEqualTo(token);
		// 새 토큰으로 다시 교체할 수 있다
		assertThat(store.rotate(rotation.newToken()).result()).isEqualTo(RefreshRotation.Result.ROTATED);
	}

	@Test
	void 직전_토큰을_유예_안에_쓰면_GRACE이고_계열은_유지된다() {
		// given: 한 번 교체해 둔다
		String token = store.issue(102L);
		RefreshRotation first = store.rotate(token);

		// when: 옛 토큰을 유예 안에 다시 쓴다
		RefreshRotation again = store.rotate(token);

		// then: 새 토큰은 주지 않고, 최신 토큰은 여전히 교체된다
		assertThat(again.result()).isEqualTo(RefreshRotation.Result.GRACE);
		assertThat(again.newToken()).isNull();
		assertThat(store.rotate(first.newToken()).result()).isEqualTo(RefreshRotation.Result.ROTATED);
	}

	@Test
	void 직전_토큰을_유예_밖에서_쓰면_REUSED이고_새_토큰도_거부된다() throws Exception {
		// given
		String token = store.issue(103L);
		RefreshRotation first = store.rotate(token);

		// when
		Thread.sleep(GRACE_PASS_MILLIS);
		RefreshRotation reused = store.rotate(token);

		// then: 계열이 지워져 최신 토큰도 쓸 수 없다
		assertThat(reused.result()).isEqualTo(RefreshRotation.Result.REUSED);
		assertThat(reused.userId()).isEqualTo(103L);
		assertThat(store.rotate(first.newToken()).result()).isEqualTo(RefreshRotation.Result.NOT_FOUND);
	}

	@Test
	void 없는_계열이나_형식이_틀린_토큰은_NOT_FOUND이다() {
		assertThat(store.rotate("a".repeat(22) + "." + "b".repeat(43)).result())
				.isEqualTo(RefreshRotation.Result.NOT_FOUND);
		assertThat(store.rotate("not-a-token").result()).isEqualTo(RefreshRotation.Result.NOT_FOUND);
		assertThat(store.rotate(null).result()).isEqualTo(RefreshRotation.Result.NOT_FOUND);
	}

	@Test
	void 엉뚱한_secret이면_REUSED로_보고_계열을_지운다() {
		// given
		String token = store.issue(104L);
		String familyId = token.substring(0, token.indexOf('.'));
		String forged = familyId + "." + "c".repeat(43);

		// when & then: 위조 토큰은 거부되고, 진짜 토큰도 함께 무효가 된다(탈취 대응)
		assertThat(store.rotate(forged).result()).isEqualTo(RefreshRotation.Result.REUSED);
		assertThat(store.rotate(token).result()).isEqualTo(RefreshRotation.Result.NOT_FOUND);
	}

	@Test
	void TTL이_설정되고_회전할_때_다시_갱신된다() {
		// given
		String token = store.issue(105L);
		String key = "rt:fam:" + token.substring(0, token.indexOf('.'));
		Long issuedTtl = redis.getExpire(key, TimeUnit.SECONDS);
		redis.expire(key, java.time.Duration.ofSeconds(100)); // 줄여 둔 뒤 교체하면 다시 늘어나는지 본다

		// when
		store.rotate(token);

		// then
		assertThat(issuedTtl).isGreaterThan(13L * 24 * 3600);
		assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isGreaterThan(13L * 24 * 3600);
	}

	@Test
	void 폐기하면_현재_토큰은_더_못_쓰고_틀린_토큰으로는_폐기되지_않는다() {
		// given
		String token = store.issue(106L);
		String familyId = token.substring(0, token.indexOf('.'));

		// when: 엉뚱한 secret으로 폐기 시도 → 계열 유지
		store.revoke(familyId + "." + "d".repeat(43));
		assertThat(redis.hasKey("rt:fam:" + familyId)).isTrue();
		store.revoke(token);

		// then
		assertThat(store.rotate(token).result()).isEqualTo(RefreshRotation.Result.NOT_FOUND);
		assertThat(redis.opsForSet().isMember("rt:user:106", familyId)).isFalse();
	}

	@Test
	void 사용자_전체_폐기는_모든_계열을_지운다() {
		// given: 같은 사용자의 두 기기
		String first = store.issue(107L);
		String second = store.issue(107L);

		// when
		store.revokeAllOf(107L);

		// then
		assertThat(store.rotate(first).result()).isEqualTo(RefreshRotation.Result.NOT_FOUND);
		assertThat(store.rotate(second).result()).isEqualTo(RefreshRotation.Result.NOT_FOUND);
	}

	@Test
	void 두_스레드가_같은_토큰을_동시에_회전하면_ROTATED_한_건과_GRACE_한_건이다() throws Exception {
		// given
		String token = store.issue(108L);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		List<Future<RefreshRotation.Result>> futures = new ArrayList<>();

		// when
		for (int i = 0; i < 2; i++) {
			futures.add(pool.submit(() -> {
				ready.countDown();
				go.await();
				return store.rotate(token).result();
			}));
		}
		ready.await();
		go.countDown();
		List<RefreshRotation.Result> results = new ArrayList<>();
		for (Future<RefreshRotation.Result> future : futures) {
			results.add(future.get(10, TimeUnit.SECONDS));
		}
		pool.shutdown();

		// then
		assertThat(results).containsExactlyInAnyOrder(RefreshRotation.Result.ROTATED, RefreshRotation.Result.GRACE);
	}
}

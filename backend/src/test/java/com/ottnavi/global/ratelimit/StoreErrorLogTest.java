package com.ottnavi.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/**
 * 저장소 오류 로그 줄이기 판단을 확인한다. 시간은 직접 조절한다.
 * 막으려는 사고: 장애 동안 요청마다 경고가 쌓이는 일, 회복 뒤 다음 장애의 첫 로그가 사라지는 일.
 */
class StoreErrorLogTest {

	private static class FakeNano implements LongSupplier {

		long now;

		@Override
		public long getAsLong() {
			return now;
		}
	}

	@Test
	void 장애의_첫_오류는_상세_로그_신호를_돌려준다() {
		// given
		RateLimitSupport.StoreErrorLog log = new RateLimitSupport.StoreErrorLog(new FakeNano());

		// when
		long result = log.recordError();

		// then
		assertThat(result).isEqualTo(RateLimitSupport.StoreErrorLog.FIRST_ERROR);
	}

	@Test
	void 같은_장애의_1분_안_오류는_로그를_남기지_않는다() {
		// given
		FakeNano nano = new FakeNano();
		RateLimitSupport.StoreErrorLog log = new RateLimitSupport.StoreErrorLog(nano);
		log.recordError();
		nano.now = Duration.ofSeconds(30).toNanos();

		// when
		long result = log.recordError();

		// then
		assertThat(result).isZero();
	}

	@Test
	void 마지막_로그_뒤_1분이_지나면_그동안_쌓인_건수를_요약으로_돌려준다() {
		// given
		FakeNano nano = new FakeNano();
		RateLimitSupport.StoreErrorLog log = new RateLimitSupport.StoreErrorLog(nano);
		log.recordError();
		nano.now = Duration.ofSeconds(20).toNanos();
		log.recordError();
		nano.now = Duration.ofSeconds(40).toNanos();
		log.recordError();
		nano.now = Duration.ofSeconds(61).toNanos();

		// when
		long result = log.recordError();

		// then
		assertThat(result).isEqualTo(3);
	}

	@Test
	void 회복된_뒤_다시_오류가_나면_처음_1건부터_상세_로그를_남긴다() {
		// given
		FakeNano nano = new FakeNano();
		RateLimitSupport.StoreErrorLog log = new RateLimitSupport.StoreErrorLog(nano);
		log.recordError();
		log.recordError();
		log.markRecovered();

		// when
		long result = log.recordError();

		// then
		assertThat(result).isEqualTo(RateLimitSupport.StoreErrorLog.FIRST_ERROR);
	}
}

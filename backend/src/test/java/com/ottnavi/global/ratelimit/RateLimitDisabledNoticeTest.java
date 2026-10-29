package com.ottnavi.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** app.rate-limit.enabled가 false일 때만 꺼짐 경고 빈이 등록되는지 검증한다(Docker·실제 Redis 불필요). */
class RateLimitDisabledNoticeTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(RateLimitDisabledNotice.class);

	@Test
	void enabled가_false면_경고_빈이_등록된다() {
		// given & when & then
		runner.withPropertyValues("app.rate-limit.enabled=false")
				.run(context -> assertThat(context).hasSingleBean(RateLimitDisabledNotice.class));
	}

	@Test
	void enabled가_true거나_없으면_경고_빈이_없다() {
		// given & when & then
		runner.run(context -> assertThat(context).doesNotHaveBean(RateLimitDisabledNotice.class));
		runner.withPropertyValues("app.rate-limit.enabled=true")
				.run(context -> assertThat(context).doesNotHaveBean(RateLimitDisabledNotice.class));
	}
}

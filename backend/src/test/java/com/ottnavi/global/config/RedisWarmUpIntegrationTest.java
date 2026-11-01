package com.ottnavi.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.ottnavi.support.IntegrationTestSupport;
import io.lettuce.core.ClientOptions;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.TestPropertySource;

/** 실제 Redis(Testcontainers)로 예열이 예외 없이 끝나는지 확인한다. 예열은 IntegrationTestSupport가 기본으로 끄므로 여기서만 켠다 */
@TestPropertySource(properties = "app.redis.warm-up.enabled=true")
class RedisWarmUpIntegrationTest extends IntegrationTestSupport {

	@Autowired RedisWarmUp redisWarmUp;
	@Autowired LettuceConnectionFactory connectionFactory;

	@Test
	void 실제_Redis에_예열해도_예외가_나지_않는다() {
		assertThatCode(() -> redisWarmUp.warmUp()).doesNotThrowAnyException();
	}

	@Test
	void 연결_대기와_명령_타임아웃은_application_yml_값을_그대로_쓴다() {
		ClientOptions options = connectionFactory.getClientConfiguration().getClientOptions().orElseThrow();

		assertThat(options.getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofSeconds(5)); // spring.data.redis.connect-timeout
		assertThat(connectionFactory.getClientConfiguration().getCommandTimeout()).isEqualTo(Duration.ofSeconds(2)); // spring.data.redis.timeout
	}
}

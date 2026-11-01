package com.ottnavi.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

class TracingPrivacyFilterTest {

	private final TracingPrivacyFilter filter = new TracingPrivacyFilter();

	@Test
	void 높은_카디널리티_값은_이름과_상관없이_모두_지운다() {
		// given
		Observation.Context context = new Observation.Context();
		context.addHighCardinalityKeyValue(KeyValue.of("db.statement", "GET refresh:hong@example.com"));
		context.addHighCardinalityKeyValue(KeyValue.of("jdbc.query[0]", "select * from users where email = 'a@b.c'"));

		// when
		filter.map(context);

		// then
		assertThat(context.getHighCardinalityKeyValues()).isEmpty();
	}

	@Test
	void 식별_속성은_낮은_카디널리티에_있어도_지우고_나머지는_남긴다() {
		// given
		Observation.Context context = new Observation.Context();
		context.addLowCardinalityKeyValue(KeyValue.of("client.address", "203.0.113.7"));
		context.addLowCardinalityKeyValue(KeyValue.of("user_agent.original", "SecretAgent"));
		context.addLowCardinalityKeyValue(KeyValue.of("enduser.id", "42"));
		context.addLowCardinalityKeyValue(KeyValue.of("net.sock.peer.addr", "10.0.0.5"));
		context.addLowCardinalityKeyValue(KeyValue.of("uri", "/api/public/titles/{mediaType}/{tmdbId}"));
		context.addLowCardinalityKeyValue(KeyValue.of("method", "GET"));

		// when
		filter.map(context);

		// then
		assertThat(context.getLowCardinalityKeyValue("client.address")).isNull();
		assertThat(context.getLowCardinalityKeyValue("user_agent.original")).isNull();
		assertThat(context.getLowCardinalityKeyValue("enduser.id")).isNull();
		assertThat(context.getLowCardinalityKeyValue("net.sock.peer.addr")).isNull();
		assertThat(context.getLowCardinalityKeyValue("uri").getValue()).isEqualTo("/api/public/titles/{mediaType}/{tmdbId}");
		assertThat(context.getLowCardinalityKeyValue("method").getValue()).isEqualTo("GET");
	}
}

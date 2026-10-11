package com.ottnavi.infra.tmdb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TmdbPropertiesTest {

    @Test
    void t21_toString에_토큰이_나오지_않는다() {
        TmdbProperties props = TmdbTestFixtures.props("secret-token-value");

        assertThat(props.toString())
                .doesNotContain("secret-token-value")
                .contains("apiToken=***");
    }
}

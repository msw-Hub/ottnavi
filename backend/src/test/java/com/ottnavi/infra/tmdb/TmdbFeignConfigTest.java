package com.ottnavi.infra.tmdb;

import static org.assertj.core.api.Assertions.assertThat;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

class TmdbFeignConfigTest {

    @Test
    void t18_토큰이_비어_있으면_인증_헤더를_붙이지_않는다() {
        RequestInterceptor interceptor = new TmdbFeignConfig().tmdbAuthInterceptor(TmdbTestFixtures.props(""));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("Authorization");
    }

    @Test
    void t18_토큰이_있으면_Bearer_헤더가_시도를_반복해도_한_번만_붙는다() {
        RequestInterceptor interceptor = new TmdbFeignConfig().tmdbAuthInterceptor(TmdbTestFixtures.props("abc"));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);
        interceptor.apply(template);   // 재시도마다 인터셉터가 다시 실행되는 상황

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer abc");
    }
}

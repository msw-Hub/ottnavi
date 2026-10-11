package com.ottnavi.infra.tmdb;

import static org.assertj.core.api.Assertions.assertThat;

import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TmdbErrorDecoderTest {

    private final TmdbErrorDecoder decoder = new TmdbErrorDecoder();

    private static Response response(int status, Map<String, Collection<String>> headers) {
        Request request = Request.create(
                Request.HttpMethod.GET, "http://localhost/3/movie/1", Map.of(), null, StandardCharsets.UTF_8, null);
        return Response.builder()
                .status(status)
                .reason("test")
                .request(request)
                .headers(headers)
                .body("{}", StandardCharsets.UTF_8)
                .build();
    }

    private static Map<String, Collection<String>> noHeaders() {
        return Map.of();
    }

    private static Map<String, Collection<String>> retryAfter(String seconds) {
        return Map.<String, Collection<String>>of("Retry-After", List.of(seconds));
    }

    @Test
    void t22_429는_Retry_After가_없어도_재시도_가능이고_retryAfter는_null이다() {
        Exception result = decoder.decode("TmdbApi#test", response(429, noHeaders()));

        assertThat(result).isInstanceOf(RetryableException.class);
        assertThat(((RetryableException) result).retryAfter()).isNull();
    }

    @Test
    void t22_5xx는_재시도_가능이다() {
        for (int status : new int[] {500, 502, 503, 504}) {
            assertThat(decoder.decode("TmdbApi#test", response(status, noHeaders())))
                    .as("상태 %d", status)
                    .isInstanceOf(RetryableException.class);
        }
    }

    @Test
    void t22_429에_Retry_After가_있으면_그_시각이_담긴다() {
        Exception result = decoder.decode("TmdbApi#test", response(429, retryAfter("2")));

        assertThat(result).isInstanceOf(RetryableException.class);
        assertThat(((RetryableException) result).retryAfter()).isNotNull();
    }

    @Test
    void t22_404는_재시도_불가이고_NotFound다() {
        Exception result = decoder.decode("TmdbApi#test", response(404, noHeaders()));

        assertThat(result).isNotInstanceOf(RetryableException.class).isInstanceOf(FeignException.NotFound.class);
    }

    @Test
    void t22_401은_재시도_불가이고_Unauthorized다() {
        Exception result = decoder.decode("TmdbApi#test", response(401, noHeaders()));

        assertThat(result).isNotInstanceOf(RetryableException.class).isInstanceOf(FeignException.Unauthorized.class);
    }

    @Test
    void t22_400에_Retry_After가_붙어_있어도_재시도_불가다() {
        Exception result = decoder.decode("TmdbApi#test", response(400, retryAfter("1")));

        assertThat(result).isNotInstanceOf(RetryableException.class).isInstanceOf(FeignException.BadRequest.class);
    }
}

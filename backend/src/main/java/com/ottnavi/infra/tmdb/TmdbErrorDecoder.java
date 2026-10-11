package com.ottnavi.infra.tmdb;

import feign.FeignException;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;

/**
 * TMDB 오류 응답을 재시도할 것(429·5xx)과 안 할 것(그 밖의 4xx)으로 나눈다.
 * Feign 기본 ErrorDecoder.Default는 상태 코드를 따지지 않고 Retry-After 헤더가 있는 응답만 RetryableException으로 감싼다(3.2절 3번).
 * 응답 본문은 로그에 넣지 않는다. FeignException 메시지에 본문이 들어 있으므로 사용자에게 보이는 메시지는 TmdbClient가 고정 문구로 다시 만든다(6절 함정 8).
 */
public class TmdbErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder fallback = new ErrorDecoder.Default();   // Retry-After 해석과 상태별 FeignException 생성을 재사용

    @Override
    public Exception decode(String methodKey, Response response) {
        Exception base = fallback.decode(methodKey, response);   // FeignException, 또는 Retry-After가 있으면 그것을 감싼 RetryableException
        int status = response.status();
        boolean transientStatus = status == 429 || status >= 500;

        if (!transientStatus) {
            // 4xx는 Retry-After가 붙어 있어도 재시도하지 않는다. Default가 감싼 RetryableException을 원래 FeignException으로 풀어 준다
            // (감쌀 때 원인(cause)으로 원래 예외를 넣는다. 응답 본문을 다시 읽을 수 없으므로 errorStatus를 또 부르지 않는다)
            if (base instanceof RetryableException && base.getCause() instanceof FeignException original) {
                return original;
            }
            return base;
        }

        if (base instanceof RetryableException) {
            return base;   // Retry-After가 있는 429·5xx: 값이 이미 담겨 있다
        }
        Long retryAfter = null;   // Retry-After가 없다. 대기 시간은 Retryer의 지수 백오프가 정한다 (Long 변수로 둬서 Date 생성자와의 모호함을 피한다)
        return new RetryableException(
                status,
                base.getMessage(),
                response.request().httpMethod(),
                base,
                retryAfter,
                response.request());
    }
}

package com.ottnavi.infra.tmdb.exception;

/** TMDB가 일시적으로 쓸 수 없다: 429·5xx·타임아웃·연결 오류가 재시도 후에도 계속, 로컬 한도 대기 초과, 응답 해석 실패 */
public class TmdbUnavailableException extends TmdbException {

    public TmdbUnavailableException(String message) {
        super(message);
    }

    public TmdbUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

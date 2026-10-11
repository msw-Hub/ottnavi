package com.ottnavi.infra.tmdb.exception;

/** 요청이나 설정이 잘못됐다: 401(토큰)·400·422 등. 재시도해도 같은 결과라 바로 던진다 */
public class TmdbRequestException extends TmdbException {

    public TmdbRequestException(String message) {
        super(message);
    }

    public TmdbRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}

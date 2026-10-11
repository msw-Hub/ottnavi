package com.ottnavi.infra.tmdb.exception;

/** TMDB에 그 ID의 리소스가 없다(404, D3). 호출한 배치는 이 작품만 건너뛰고 계속한다(7.1절) */
public class TmdbNotFoundException extends TmdbException {

    public TmdbNotFoundException(String message) {
        super(message);
    }

    public TmdbNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

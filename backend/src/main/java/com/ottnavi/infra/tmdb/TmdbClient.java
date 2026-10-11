package com.ottnavi.infra.tmdb;

import com.ottnavi.infra.tmdb.dto.TmdbMovieDetail;
import com.ottnavi.infra.tmdb.dto.TmdbMovieSummary;
import com.ottnavi.infra.tmdb.dto.TmdbPage;
import com.ottnavi.infra.tmdb.dto.TmdbProviderListResponse;
import com.ottnavi.infra.tmdb.dto.TmdbTvDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvSeasonDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvSummary;
import com.ottnavi.infra.tmdb.dto.TmdbWatchProvidersResponse;
import com.ottnavi.infra.tmdb.exception.TmdbException;
import com.ottnavi.infra.tmdb.exception.TmdbNotFoundException;
import com.ottnavi.infra.tmdb.exception.TmdbRequestException;
import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;
import feign.FeignException;
import feign.codec.DecodeException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 소비 Task(042~044, 050)가 쓰는 TMDB 호출 창구(D5).
 * 고정 쿼리 값(KR, ko-KR, flatrate)을 채우고, 모든 호출을 call()로 감싸 tmdb.call 카운터를 세고 Feign 예외를 TmdbException으로 바꾼다.
 * 반환 DTO(infra.tmdb.dto)는 받자마자 도메인 값으로 바꿔 쓰고 오래 들고 다니거나 api 응답에 내보내지 않는다(6절 함정 12).
 * 외부 호출이므로 트랜잭션 밖에서 부른다(수집 배치만 예외, TECH T-2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbClient {

    private static final String REGION = "KR";                        // PRD: 한국 제공처만 본다
    private static final String LANGUAGE_KO = "ko-KR";
    private static final String MONETIZATION_FLATRATE = "flatrate";   // 후보 수집은 구독형만

    // tmdb.call 카운터: 이름과 태그 값. 값 집합은 닫혀 있다(작품 ID·검색어·URL·페이지 번호는 태그로 쓰지 않는다, 3.6절)
    private static final String METRIC_NAME = "tmdb.call";
    private static final String EP_DISCOVER_MOVIE = "discover_movie";
    private static final String EP_DISCOVER_TV = "discover_tv";
    private static final String EP_SEARCH_MOVIE = "search_movie";
    private static final String EP_SEARCH_TV = "search_tv";
    private static final String EP_MOVIE_DETAIL = "movie_detail";
    private static final String EP_TV_DETAIL = "tv_detail";
    private static final String EP_TV_SEASON = "tv_season";
    private static final String EP_MOVIE_PROVIDERS = "movie_providers";
    private static final String EP_TV_SEASON_PROVIDERS = "tv_season_providers";
    private static final String EP_PROVIDER_LIST = "provider_list";
    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_NOT_FOUND = "not_found";
    private static final String RESULT_RATE_LIMITED = "rate_limited";
    private static final String RESULT_SERVER_ERROR = "server_error";
    private static final String RESULT_CLIENT_ERROR = "client_error";
    private static final String RESULT_IO_ERROR = "io_error";
    private static final String RESULT_LOCAL_LIMIT = "local_limit";
    private static final String RESULT_DECODE_ERROR = "decode_error";

    private final TmdbApi api;
    private final MeterRegistry meterRegistry;

    // 후보 수집·검색

    /** 제공처(ID, 쉼표 AND·파이프 OR)별 KR 구독 후보 영화를 한 쪽씩 가져온다 */
    public TmdbPage<TmdbMovieSummary> discoverMovies(String withWatchProviders, int page) {
        return call(EP_DISCOVER_MOVIE,
                () -> api.discoverMovies(withWatchProviders, REGION, MONETIZATION_FLATRATE, LANGUAGE_KO, page));
    }

    /** 제공처별 KR 구독 후보 드라마를 한 쪽씩 가져온다 */
    public TmdbPage<TmdbTvSummary> discoverTvs(String withWatchProviders, int page) {
        return call(EP_DISCOVER_TV,
                () -> api.discoverTvs(withWatchProviders, REGION, MONETIZATION_FLATRATE, LANGUAGE_KO, page));
    }

    /** 영화를 제목으로 검색한다 (성인물 제외). query는 사용자 입력이라 로그·태그에 쓰지 않는다 */
    public TmdbPage<TmdbMovieSummary> searchMovies(String query, int page) {
        return call(EP_SEARCH_MOVIE, () -> api.searchMovies(query, LANGUAGE_KO, page, false));
    }

    /** 드라마를 제목으로 검색한다 */
    public TmdbPage<TmdbTvSummary> searchTvs(String query, int page) {
        return call(EP_SEARCH_TV, () -> api.searchTvs(query, LANGUAGE_KO, page, null));
    }

    // 상세·시즌

    /** 영화 상세를 가져온다. appendToResponse에 "credits"를 주면 출연진이 같이 온다(null이면 안 붙인다) */
    public TmdbMovieDetail getMovie(long movieId, String language, String appendToResponse) {
        return call(EP_MOVIE_DETAIL, () -> api.getMovie(movieId, language, appendToResponse));
    }

    /** 드라마 상세(시즌 목록 포함)를 가져온다 */
    public TmdbTvDetail getTv(long tvId, String language, String appendToResponse) {
        return call(EP_TV_DETAIL, () -> api.getTv(tvId, language, appendToResponse));
    }

    /** 드라마 한 시즌의 회차 목록을 가져온다 */
    public TmdbTvSeasonDetail getTvSeason(long tvId, int seasonNumber, String language) {
        return call(EP_TV_SEASON, () -> api.getTvSeason(tvId, seasonNumber, language));
    }

    // 제공처

    /** 영화의 나라별 시청 제공처를 가져온다(KR 골라 쓰기는 소비 쪽) */
    public TmdbWatchProvidersResponse getMovieWatchProviders(long movieId) {
        return call(EP_MOVIE_PROVIDERS, () -> api.getMovieWatchProviders(movieId));
    }

    /** 드라마 한 시즌의 나라별 시청 제공처를 가져온다 */
    public TmdbWatchProvidersResponse getTvSeasonWatchProviders(long tvId, int seasonNumber) {
        return call(EP_TV_SEASON_PROVIDERS, () -> api.getTvSeasonWatchProviders(tvId, seasonNumber));
    }

    /** KR의 제공처 전체 목록을 가져온다. 우리 7곳 외 제공처도 거르지 않고 돌려준다(저장은 Task 043) */
    public TmdbProviderListResponse listProviders(TmdbMediaType mediaType) {
        return call(EP_PROVIDER_LIST, () -> api.listProviders(mediaType.getPath(), REGION, LANGUAGE_KO));
    }

    // 공통 처리

    /** 호출을 실행하고 결과를 tmdb.call로 센다. Feign 예외는 TmdbException으로 바꿔 던진다 */
    private <T> T call(String endpoint, Supplier<T> action) {
        try {
            T result = action.get();
            count(endpoint, RESULT_SUCCESS);
            return result;
        } catch (TmdbException e) {
            // 호출 전 속도 제한 인터셉터가 던진 예외(로컬 한도 대기 초과 등). 이미 변환된 예외라 그대로 올린다
            count(endpoint, RESULT_LOCAL_LIMIT);
            log.warn("TMDB 호출 전 로컬 한도에서 포기했다: endpoint={}", endpoint);
            throw e;
        } catch (FeignException e) {
            // 재시도 후에도 실패한 경우와 응답 해석 실패(DecodeException 포함)
            Failure failure = toFailure(e);
            count(endpoint, failure.result());
            logFailure(endpoint, e.status(), failure.result());
            throw failure.exception();
        }
    }

    /** FeignException을 결과 태그와 TmdbException 쌍으로 바꾼다. 메시지는 고정 문구, 원인은 cause로 남긴다 */
    private Failure toFailure(FeignException e) {
        if (e instanceof DecodeException) {
            // 상태가 200이어도 JSON을 DTO로 못 바꾼 경우. 재시도 대상이 아니며 TMDB 응답 형식 변화를 의심한다
            return new Failure(RESULT_DECODE_ERROR, new TmdbUnavailableException("TMDB 응답을 해석하지 못했다", e));
        }
        int status = e.status();
        if (status == 404) {
            return new Failure(RESULT_NOT_FOUND, new TmdbNotFoundException("TMDB에 요청한 리소스가 없다", e));
        }
        if (status == 429) {
            return new Failure(RESULT_RATE_LIMITED, new TmdbUnavailableException("TMDB 호출 한도 초과로 재시도 후에도 실패했다", e));
        }
        if (status >= 500) {
            return new Failure(RESULT_SERVER_ERROR, new TmdbUnavailableException("TMDB 서버 오류로 호출에 실패했다", e));
        }
        if (status == -1) {
            // 연결 실패·읽기 타임아웃·연결 끊김: Feign이 IOException을 상태 -1의 RetryableException으로 감싼다
            return new Failure(RESULT_IO_ERROR, new TmdbUnavailableException("TMDB에 연결하지 못했거나 응답 시간을 넘겼다", e));
        }
        return new Failure(RESULT_CLIENT_ERROR, new TmdbRequestException("TMDB가 요청을 거절했다. 요청 값과 토큰 설정을 확인한다", e));
    }

    /** 최종 실패를 심각도에 맞게 남긴다. e.request()나 응답 본문은 찍지 않는다(헤더에 토큰이 있다) */
    private void logFailure(String endpoint, int status, String result) {
        if (status == 401) {
            log.error("TMDB 인증에 실패했다. TMDB_API_KEY(읽기 액세스 토큰) 설정을 확인한다: endpoint={}", endpoint);
        } else {
            log.warn("TMDB 호출이 실패했다: endpoint={}, result={}, status={}", endpoint, result, status);
        }
    }

    private void count(String endpoint, String result) {
        meterRegistry.counter(METRIC_NAME, "endpoint", endpoint, "result", result).increment();
    }

    /** 변환 결과 묶음: 카운터 태그 값 + 던질 예외 */
    private record Failure(String result, TmdbException exception) {
    }
}

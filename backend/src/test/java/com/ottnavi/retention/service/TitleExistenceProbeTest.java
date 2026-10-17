package com.ottnavi.retention.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.service.model.StaleTitle;
import com.ottnavi.infra.tmdb.TmdbClient;
import com.ottnavi.infra.tmdb.dto.TmdbMovieDetail;
import com.ottnavi.infra.tmdb.dto.TmdbTvDetail;
import com.ottnavi.infra.tmdb.exception.TmdbNotFoundException;
import com.ottnavi.infra.tmdb.exception.TmdbRequestException;
import com.ottnavi.infra.tmdb.exception.TmdbUnavailableException;
import com.ottnavi.retention.service.model.TitleExistence;
import org.junit.jupiter.api.Test;

/**
 * TMDB 존재 확인의 판정 규칙. 막으려는 사고: 일시 장애(429·5xx)나 빈 응답을 "사라짐"으로 오판해 멀쩡한 작품을 지우는 일.
 */
class TitleExistenceProbeTest {

	private final TmdbClient tmdbClient = mock(TmdbClient.class);
	private final TitleExistenceProbe probe = new TitleExistenceProbe(tmdbClient);

	private final StaleTitle movie = new StaleTitle(1L, MediaType.MOVIE, 100);
	private final StaleTitle tv = new StaleTitle(2L, MediaType.TV, 200);

	@Test
	void 영화_상세가_응답하면_EXISTS다() {
		// given
		when(tmdbClient.getMovie(eq(100L), anyString(), isNull())).thenReturn(mock(TmdbMovieDetail.class));

		// when
		TitleExistence result = probe.probe(movie);

		// then
		assertThat(result).isEqualTo(TitleExistence.EXISTS);
	}

	@Test
	void 영화_상세가_404면_GONE이다() {
		// given
		when(tmdbClient.getMovie(eq(100L), anyString(), isNull())).thenThrow(new TmdbNotFoundException("없음"));

		// when
		TitleExistence result = probe.probe(movie);

		// then
		assertThat(result).isEqualTo(TitleExistence.GONE);
	}

	@Test
	void 드라마_상세가_404면_GONE이다() {
		// given
		when(tmdbClient.getTv(eq(200L), anyString(), isNull())).thenThrow(new TmdbNotFoundException("없음"));

		// when
		TitleExistence result = probe.probe(tv);

		// then
		assertThat(result).isEqualTo(TitleExistence.GONE);
	}

	@Test
	void 한도_초과나_서버_오류는_삭제_근거가_아니라_UNKNOWN이다() {
		// given
		when(tmdbClient.getMovie(eq(100L), anyString(), isNull())).thenThrow(new TmdbUnavailableException("429"));
		when(tmdbClient.getTv(eq(200L), anyString(), isNull())).thenThrow(new TmdbRequestException("거절"));

		// when
		TitleExistence movieResult = probe.probe(movie);
		TitleExistence tvResult = probe.probe(tv);

		// then
		assertThat(movieResult).isEqualTo(TitleExistence.UNKNOWN);
		assertThat(tvResult).isEqualTo(TitleExistence.UNKNOWN);
	}

	@Test
	void 응답_본문이_비어_있으면_UNKNOWN이다() {
		// given
		when(tmdbClient.getTv(eq(200L), anyString(), isNull())).thenReturn((TmdbTvDetail) null);

		// when
		TitleExistence result = probe.probe(tv);

		// then
		assertThat(result).isEqualTo(TitleExistence.UNKNOWN);
	}
}

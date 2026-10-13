package com.ottnavi.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** Title 매핑·리포지토리 테스트(Task 035). 테스트마다 롤백한다 */
@Transactional
class TitleRepositoryTest extends IntegrationTestSupport {

    // PostgreSQL TIMESTAMPTZ는 마이크로초까지 저장하므로 나노초 없는 값으로 비교한다
    private static final Instant FETCHED_AT = Instant.parse("2026-10-13T01:02:03Z");

    @Autowired
    TitleRepository titleRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void 작품을_저장하고_읽으면_숫자_날짜_enum_값이_그대로_돌아온다() {
        Title title = Title.create(MediaType.TV, 1396, "브레이킹 배드", FETCHED_AT);
        title.updateDetail("브레이킹 배드", "Breaking Bad", "줄거리", "ko", LocalDate.of(2008, 1, 20),
                "/poster.jpg", "/backdrop.jpg", "en", "Ended", new BigDecimal("123.457"), new BigDecimal("8.92"),
                FETCHED_AT);
        title.changeRefreshTier(RefreshTier.DAILY);
        titleRepository.saveAndFlush(title);
        entityManager.clear(); // 1차 캐시를 비워 DB에서 다시 읽게 한다

        Title found = titleRepository.findByMediaTypeAndTmdbId(MediaType.TV, 1396).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getPopularity()).isEqualByComparingTo("123.457");
        assertThat(found.getVoteAverage()).isEqualByComparingTo("8.92");
        assertThat(found.getReleaseDate()).isEqualTo(LocalDate.of(2008, 1, 20));
        assertThat(found.getFetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(found.getRefreshTier()).isEqualTo(RefreshTier.DAILY);
        assertThat(found.getMediaType()).isEqualTo(MediaType.TV);
        assertThat(found.getCreatedAt()).isNotNull(); // BaseTimeEntity가 채운다
    }

    @Test
    void 같은_종류의_같은_TMDB_ID를_두_번_저장하면_유니크_위반이다() {
        titleRepository.saveAndFlush(Title.create(MediaType.MOVIE, 550, "파이트 클럽", FETCHED_AT));

        assertThatThrownBy(() -> titleRepository.saveAndFlush(Title.create(MediaType.MOVIE, 550, "중복", FETCHED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 종류가_다르면_같은_TMDB_ID를_저장할_수_있다() {
        titleRepository.saveAndFlush(Title.create(MediaType.MOVIE, 550, "영화 550", FETCHED_AT));
        titleRepository.saveAndFlush(Title.create(MediaType.TV, 550, "드라마 550", FETCHED_AT));

        assertThat(titleRepository.findByMediaTypeAndTmdbId(MediaType.MOVIE, 550)).isPresent();
        assertThat(titleRepository.findByMediaTypeAndTmdbId(MediaType.TV, 550)).isPresent();
        assertThat(titleRepository.findByMediaTypeAndTmdbId(MediaType.TV, 551)).isEmpty();
    }
}

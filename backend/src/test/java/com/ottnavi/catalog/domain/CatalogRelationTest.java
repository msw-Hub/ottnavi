package com.ottnavi.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.provider.domain.OttService;
import com.ottnavi.provider.domain.OttServiceRepository;
import com.ottnavi.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** 시청 단위·제공 상태·장르·출연진 매핑 테스트(Task 035). 연관관계와 복합 기본키가 DB와 맞는지 본다. 테스트마다 롤백한다 */
@Transactional
class CatalogRelationTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-10-13T01:02:03Z");

    @Autowired
    TitleRepository titleRepository;
    @Autowired
    WatchUnitRepository watchUnitRepository;
    @Autowired
    AvailabilityRepository availabilityRepository;
    @Autowired
    AvailabilityChangeRepository availabilityChangeRepository;
    @Autowired
    GenreRepository genreRepository;
    @Autowired
    TitleGenreRepository titleGenreRepository;
    @Autowired
    TitleCastRepository titleCastRepository;
    @Autowired
    OttServiceRepository ottServiceRepository;
    @Autowired
    EntityManager entityManager;

    private Title saveTitle(MediaType mediaType, int tmdbId) {
        return titleRepository.saveAndFlush(Title.create(mediaType, tmdbId, "테스트 작품", NOW));
    }

    private OttService anyService() {
        return ottServiceRepository.findAll().get(0);
    }

    @Test
    void 영화와_시즌_시청_단위가_저장되고_영화는_시즌_번호가_없다() {
        Title title = saveTitle(MediaType.TV, 2001);
        WatchUnit season = watchUnitRepository.save(
                WatchUnit.createSeason(title, 1, 5001, "시즌 1", 10, 8, 480, NOW));
        Title movieTitle = saveTitle(MediaType.MOVIE, 2002);
        WatchUnit movie = watchUnitRepository.save(WatchUnit.createMovie(movieTitle, 120, NOW));
        entityManager.flush();
        entityManager.clear();

        WatchUnit foundSeason = watchUnitRepository.findById(season.getId()).orElseThrow();
        WatchUnit foundMovie = watchUnitRepository.findById(movie.getId()).orElseThrow();

        assertThat(foundSeason.getUnitType()).isEqualTo(UnitType.SEASON);
        assertThat(foundSeason.getSeasonNumber()).isEqualTo(1);
        assertThat(foundSeason.getAiredEpisodeCount()).isEqualTo(8);
        assertThat(foundMovie.getUnitType()).isEqualTo(UnitType.MOVIE);
        assertThat(foundMovie.getSeasonNumber()).isNull();
        assertThat(foundMovie.getEpisodeCount()).isEqualTo(1);
    }

    @Test
    void 제공_상태가_저장되고_모름_이유까지_그대로_돌아온다() {
        WatchUnit unit = watchUnitRepository.save(WatchUnit.createMovie(saveTitle(MediaType.MOVIE, 2003), 100, NOW));
        Availability saved = availabilityRepository.save(Availability.create(
                unit, anyService(), AvailabilityStatus.UNKNOWN, UnknownReason.NO_KR_DATA, false, false, NOW));
        entityManager.flush();
        entityManager.clear();

        Availability found = availabilityRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(AvailabilityStatus.UNKNOWN);
        assertThat(found.getUnknownReason()).isEqualTo(UnknownReason.NO_KR_DATA);
        assertThat(found.getCheckedAt()).isEqualTo(NOW);
        assertThat(found.getWatchUnit().getId()).isEqualTo(unit.getId()); // LAZY 프록시여도 id는 읽힌다
    }

    @Test
    void 제공_상태를_바꾸면_이유와_플래그가_함께_바뀌어_저장된다() {
        WatchUnit unit = watchUnitRepository.save(WatchUnit.createMovie(saveTitle(MediaType.MOVIE, 2004), 100, NOW));
        Availability availability = availabilityRepository.save(Availability.create(
                unit, anyService(), AvailabilityStatus.UNKNOWN, UnknownReason.STRUCTURAL_GAP, false, false, NOW));

        availability.changeState(AvailabilityStatus.AVAILABLE, null, true, false, NOW.plusSeconds(60));
        entityManager.flush(); // 더티 체킹으로 UPDATE가 나간다. DB CHECK도 함께 통과해야 한다
        entityManager.clear();

        Availability found = availabilityRepository.findById(availability.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(found.getUnknownReason()).isNull();
        assertThat(found.isInStandard()).isTrue();
    }

    @Test
    void 같은_시청_단위와_서비스의_제공_상태를_두_번_저장하면_유니크_위반이다() {
        WatchUnit unit = watchUnitRepository.save(WatchUnit.createMovie(saveTitle(MediaType.MOVIE, 2005), 100, NOW));
        OttService service = anyService();
        availabilityRepository.saveAndFlush(Availability.create(
                unit, service, AvailabilityStatus.AVAILABLE, null, true, false, NOW));

        assertThatThrownBy(() -> availabilityRepository.saveAndFlush(Availability.create(
                unit, service, AvailabilityStatus.NOT_AVAILABLE, null, false, false, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 변경_이력은_신규일_때_이전_상태와_실행_ID가_null로_저장된다() {
        WatchUnit unit = watchUnitRepository.save(WatchUnit.createMovie(saveTitle(MediaType.MOVIE, 2006), 100, NOW));
        AvailabilityChange saved = availabilityChangeRepository.save(AvailabilityChange.create(
                unit, anyService(), null, AvailabilityStatus.AVAILABLE, null, NOW));
        entityManager.flush();
        entityManager.clear();

        AvailabilityChange found = availabilityChangeRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getBeforeStatus()).isNull();
        assertThat(found.getAfterStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(found.getJobExecutionId()).isNull();
    }

    @Test
    void 작품_장르_연결은_복합_키로_저장되고_다시_읽힌다() {
        Title title = saveTitle(MediaType.MOVIE, 2007);
        Genre genre = genreRepository.saveAndFlush(Genre.create(16, "애니메이션"));
        titleGenreRepository.saveAndFlush(TitleGenre.create(title, genre));
        entityManager.clear();

        TitleGenre found = titleGenreRepository.findById(new TitleGenreId(title.getId(), 16)).orElseThrow();

        assertThat(found.getId().getTitleId()).isEqualTo(title.getId());
        assertThat(found.getGenre().getName()).isEqualTo("애니메이션");
        assertThat(titleGenreRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_작품에_같은_출연_순서를_두_번_저장하면_유니크_위반이다() {
        Title title = saveTitle(MediaType.MOVIE, 2008);
        TitleCast first = titleCastRepository.saveAndFlush(TitleCast.create(title, 0, 100, "배우 A", null, null));
        assertThat(first.getCharacterName()).isNull(); // 배역은 비어 있을 수 있다

        assertThatThrownBy(() -> titleCastRepository.saveAndFlush(TitleCast.create(title, 0, 101, "배우 B", "배역", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

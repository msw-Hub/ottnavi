package com.ottnavi.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.catalog.service.model.RefreshTierRealignment;
import com.ottnavi.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Task 077: 찜 현황에 맞춰 작품의 refresh_tier를 한꺼번에 맞추는 벌크 쿼리를 DB(Testcontainers)로 시험한다(Docker 필요) */
class TitleRefreshTierServiceTest extends IntegrationTestSupport {

    @Autowired
    private TitleRefreshTierService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void 정리() {
        jdbcTemplate.update("DELETE FROM wishlist_item");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'refresh-tier-%'");
        for (String table : List.of("availability_change", "availability", "watch_unit", "title_cast", "title_genre",
                "title", "genre")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @Test
    void 찜이_있는_작품은_DAILY로_올리고_찜이_없는_작품은_WEEKLY로_둔다() {
        // given
        long wished = insertTitle(1, "WEEKLY");
        long notWished = insertTitle(2, "WEEKLY");
        long userId = insertUser("refresh-tier-a@test.com");
        insertWishlist(userId, insertUnit(wished, null));

        // when
        RefreshTierRealignment result = service.realignByWishlist();

        // then
        assertThat(refreshTierOf(wished)).isEqualTo("DAILY");
        assertThat(refreshTierOf(notWished)).isEqualTo("WEEKLY");
        assertThat(result.promotedToDaily()).isEqualTo(1);
        assertThat(result.demotedToWeekly()).isEqualTo(0);
    }

    @Test
    void 찜이_모두_사라진_작품은_다음_재맞춤에서_WEEKLY로_돌아온다() {
        // given: 찜이 있어 DAILY가 된 작품
        long title = insertTitle(1, "WEEKLY");
        long userId = insertUser("refresh-tier-a@test.com");
        insertWishlist(userId, insertUnit(title, null));
        service.realignByWishlist();
        assertThat(refreshTierOf(title)).isEqualTo("DAILY");

        // when: 회원 탈퇴처럼 사용자가 지워져 찜이 함께 사라진다
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        RefreshTierRealignment result = service.realignByWishlist();

        // then
        assertThat(refreshTierOf(title)).isEqualTo("WEEKLY");
        assertThat(result.demotedToWeekly()).isEqualTo(1);
    }

    @Test
    void 여러_사용자가_같은_작품의_여러_시즌을_찜해도_작품은_한_번만_DAILY가_된다() {
        // given: 한 드라마의 시즌 1·2를 두 사용자가 나눠 찜했다
        long drama = insertTitle(1, "WEEKLY");
        long season1 = insertUnit(drama, 1);
        long season2 = insertUnit(drama, 2);
        long userA = insertUser("refresh-tier-a@test.com");
        long userB = insertUser("refresh-tier-b@test.com");
        insertWishlist(userA, season1);
        insertWishlist(userB, season1);
        insertWishlist(userB, season2);

        // when
        RefreshTierRealignment result = service.realignByWishlist();

        // then: 작품 1건이 한 번 바뀐다
        assertThat(refreshTierOf(drama)).isEqualTo("DAILY");
        assertThat(result.promotedToDaily()).isEqualTo(1);
    }

    @Test
    void 다_봤음_표시가_있어도_찜_행이_있으면_DAILY다() {
        // given
        long title = insertTitle(1, "WEEKLY");
        long userId = insertUser("refresh-tier-a@test.com");
        long wishlistId = insertWishlist(userId, insertUnit(title, null));
        jdbcTemplate.update("UPDATE wishlist_item SET watched_at = now() WHERE id = ?", wishlistId);

        // when
        service.realignByWishlist();

        // then
        assertThat(refreshTierOf(title)).isEqualTo("DAILY");
    }

    @Test
    void 이미_맞는_작품은_건드리지_않아_두_번째_재맞춤은_0건이다() {
        // given
        long title = insertTitle(1, "WEEKLY");
        long userId = insertUser("refresh-tier-a@test.com");
        insertWishlist(userId, insertUnit(title, null));
        service.realignByWishlist();

        // when
        RefreshTierRealignment second = service.realignByWishlist();

        // then
        assertThat(second.promotedToDaily()).isEqualTo(0);
        assertThat(second.demotedToWeekly()).isEqualTo(0);
    }

    // 도우미

    private long insertTitle(int tmdbId, String refreshTier) {
        return jdbcTemplate.queryForObject("INSERT INTO title (media_type, tmdb_id, title, refresh_tier, fetched_at) "
                + "VALUES ('MOVIE', ?, ?, ?, now()) RETURNING id", Long.class, tmdbId, "작품 " + tmdbId, refreshTier);
    }

    /** seasonNumber가 null이면 영화 단위, 아니면 시즌 단위 */
    private long insertUnit(long titleId, Integer seasonNumber) {
        String unitType = "MOVIE";
        if (seasonNumber != null) {
            unitType = "SEASON";
        }
        return jdbcTemplate.queryForObject("INSERT INTO watch_unit (title_id, unit_type, season_number, "
                + "total_runtime_min, fetched_at) VALUES (?, ?, ?, 100, now()) RETURNING id", Long.class,
                titleId, unitType, seasonNumber);
    }

    private long insertUser(String email) {
        return jdbcTemplate.queryForObject("INSERT INTO users (email, name, oauth_provider, oauth_subject) "
                + "VALUES (?, '재맞춤시험', 'GOOGLE', ?) RETURNING id", Long.class, email, "sub-" + email);
    }

    private long insertWishlist(long userId, long unitId) {
        return jdbcTemplate.queryForObject("INSERT INTO wishlist_item (user_id, watch_unit_id, priority) "
                + "VALUES (?, ?, 'MUST') RETURNING id", Long.class, userId, unitId);
    }

    private String refreshTierOf(long titleId) {
        return jdbcTemplate.queryForObject("SELECT refresh_tier FROM title WHERE id = ?", String.class, titleId);
    }
}

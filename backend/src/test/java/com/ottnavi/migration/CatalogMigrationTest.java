package com.ottnavi.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V2~V4 마이그레이션 테스트(Task 033): 시드가 들어가고, 정상 데이터는 통과하며, 잘못된 데이터는 DB가 거부한다.
 * 클래스에 @Transactional을 붙여 테스트마다 롤백한다. PostgreSQL은 문장이 실패하면 그 트랜잭션을 더 쓸 수 없으므로
 * 거부를 확인하는 테스트는 실패하는 문장을 하나만 둔다.
 */
@Transactional
class CatalogMigrationTest extends IntegrationTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    // 작품 한 건을 넣고 내부 id를 돌려준다
    private long insertTitle(String mediaType, int tmdbId) {
        return jdbc.queryForObject(
                "INSERT INTO title (media_type, tmdb_id, title, fetched_at) VALUES (?, ?, '테스트 작품', now()) RETURNING id",
                Long.class, mediaType, tmdbId);
    }

    // 시청 단위 한 건을 넣고 내부 id를 돌려준다. 영화면 seasonNumber를 null로 넘긴다
    private long insertWatchUnit(long titleId, String unitType, Integer seasonNumber) {
        return jdbc.queryForObject(
                "INSERT INTO watch_unit (title_id, unit_type, season_number, total_runtime_min, fetched_at) "
                        + "VALUES (?, ?, ?, 120, now()) RETURNING id",
                Long.class, titleId, unitType, seasonNumber);
    }

    @Test
    void 제공처_시드_8행이_모두_서비스에_연결돼_있다() {
        Integer mapped = jdbc.queryForObject(
                "SELECT count(*) FROM tmdb_provider WHERE mapping_state = 'MAPPED' AND ott_service_id IS NOT NULL",
                Integer.class);
        String adTier = jdbc.queryForObject("SELECT tier FROM tmdb_provider WHERE provider_id = 1796", String.class);

        assertThat(mapped).isEqualTo(8);
        assertThat(adTier).isEqualTo("AD");
    }

    @Test
    void 영화와_드라마는_같은_TMDB_ID를_쓸_수_있다() {
        insertTitle("MOVIE", 1000);
        insertTitle("TV", 1000);

        Integer count = jdbc.queryForObject("SELECT count(*) FROM title WHERE tmdb_id = 1000", Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void 같은_종류_같은_TMDB_ID는_거부한다() {
        insertTitle("MOVIE", 1001);

        assertThatThrownBy(() -> insertTitle("MOVIE", 1001)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 영화_시청단위는_시즌이_NULL이어도_작품당_하나만_허용한다() {
        long titleId = insertTitle("MOVIE", 1002);
        insertWatchUnit(titleId, "MOVIE", null);

        assertThatThrownBy(() -> insertWatchUnit(titleId, "MOVIE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 시즌인데_시즌_번호가_없으면_거부한다() {
        long titleId = insertTitle("TV", 1003);

        assertThatThrownBy(() -> insertWatchUnit(titleId, "SEASON", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 모름_상태에_이유가_없으면_거부한다() {
        long unitId = insertWatchUnit(insertTitle("MOVIE", 1004), "MOVIE", null);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO availability (watch_unit_id, ott_service_id, status, checked_at) VALUES (?, 1, 'UNKNOWN', now())",
                unitId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 모름_상태에_이유가_있으면_저장된다() {
        long unitId = insertWatchUnit(insertTitle("MOVIE", 1005), "MOVIE", null);

        int rows = jdbc.update(
                "INSERT INTO availability (watch_unit_id, ott_service_id, status, unknown_reason, checked_at) "
                        + "VALUES (?, 7, 'UNKNOWN', 'STRUCTURAL_GAP', now())",
                unitId);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void 매핑됨_상태인데_서비스가_없으면_거부한다() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO tmdb_provider (provider_id, tmdb_name, mapping_state) VALUES (99999, '테스트', 'MAPPED')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_상품의_같은_시작일_요금은_거부한다() {
        long productId = jdbc.queryForObject(
                "INSERT INTO subscription_product (name, product_type) VALUES ('테스트 상품', 'SINGLE') RETURNING id",
                Long.class);
        String sql = "INSERT INTO product_price (product_id, monthly_price, effective_from, last_verified_at) "
                + "VALUES (?, 13500, DATE '2026-10-01', now())";
        jdbc.update(sql, productId);

        assertThatThrownBy(() -> jdbc.update(sql, productId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 시청단위가_참조하는_작품은_지울_수_없다() {
        long titleId = insertTitle("MOVIE", 1006);
        insertWatchUnit(titleId, "MOVIE", null);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM title WHERE id = ?", titleId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 변경_이력은_실행_ID_인덱스를_갖는다() {
        Integer indexes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_availability_change_job'", Integer.class);
        assertThat(indexes).isEqualTo(1);
    }

    @Test
    void Spring_Batch_메타_테이블_6개가_만들어져_있다() {
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 'batch_%'",
                Integer.class);
        assertThat(tables).isEqualTo(6);
    }
}

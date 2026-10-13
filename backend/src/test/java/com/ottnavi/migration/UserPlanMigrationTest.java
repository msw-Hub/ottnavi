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
 * V5~V7 마이그레이션 테스트(Task 034): 유니크·CHECK가 잘못된 데이터를 막고, FK 삭제 동작(CASCADE, SET NULL)이 ERD대로 움직인다.
 * ddl-auto=validate는 ON DELETE를 검사하지 않으므로 이 테스트가 FK 동작의 기준이다.
 * 클래스에 @Transactional을 붙여 테스트마다 롤백한다. PostgreSQL은 문장이 실패하면 그 트랜잭션을 더 쓸 수 없으므로
 * 거부를 확인하는 테스트는 실패하는 문장을 하나만 둔다.
 */
@Transactional
class UserPlanMigrationTest extends IntegrationTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    // 사용자 한 명을 넣고 내부 id를 돌려준다
    private long insertUser(String email) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, name, oauth_provider, oauth_subject) VALUES (?, '테스트', 'GOOGLE', ?) RETURNING id",
                Long.class, email, "sub-" + email);
    }

    // 플랜 한 건을 넣고 id를 돌려준다. DRAFT만 activated_at이 NULL이어야 한다(CHECK)
    private long insertPlan(long userId, String status, String planType) {
        String activatedAt = status.equals("DRAFT") ? "NULL" : "now()";
        return jdbc.queryForObject(
                "INSERT INTO plan (user_id, status, plan_type, start_month, monthly_budget, monthly_watch_minutes, algorithm, "
                        + "must_total, must_completed, score, total_cost, all_subscribe_cost, data_as_of, activated_at) "
                        + "VALUES (?, ?, ?, DATE '2026-11-01', 30000, 1200, 'EXACT', 1, 1, 2, 10000, 20000, now(), "
                        + activatedAt + ") RETURNING id",
                Long.class, userId, status, planType);
    }

    // 시청 단위(영화) 한 건을 만들고 id를 돌려준다
    private long insertWatchUnit(int tmdbId) {
        long titleId = jdbc.queryForObject(
                "INSERT INTO title (media_type, tmdb_id, title, fetched_at) VALUES ('MOVIE', ?, '테스트 작품', now()) RETURNING id",
                Long.class, tmdbId);
        return jdbc.queryForObject(
                "INSERT INTO watch_unit (title_id, unit_type, season_number, total_runtime_min, fetched_at) "
                        + "VALUES (?, 'MOVIE', NULL, 120, now()) RETURNING id",
                Long.class, titleId);
    }

    // 구독 상품 한 건을 만들고 id를 돌려준다
    private long insertProduct() {
        return jdbc.queryForObject(
                "INSERT INTO subscription_product (name, product_type) VALUES ('테스트 상품', 'SINGLE') RETURNING id",
                Long.class);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    void DRAFT를_지우면_하위_4개_테이블_행도_사라진다() {
        long planId = insertPlan(insertUser("a@test.com"), "DRAFT", "RECOMMENDED");
        long monthId = jdbc.queryForObject(
                "INSERT INTO plan_month (plan_id, month_index, month, confirmed, cost) "
                        + "VALUES (?, 0, DATE '2026-11-01', TRUE, 5000) RETURNING id",
                Long.class, planId);
        jdbc.update(
                "INSERT INTO plan_month_product (plan_month_id, product_id, applied_price, price_source) VALUES (?, ?, 5000, 'ADMIN')",
                monthId, insertProduct());
        long itemId = jdbc.queryForObject(
                "INSERT INTO plan_item (plan_id, watch_unit_id, priority, outcome, completed_month_index) "
                        + "VALUES (?, ?, 'MUST', 'SCHEDULED', 0) RETURNING id",
                Long.class, planId, insertWatchUnit(2001));
        jdbc.update(
                "INSERT INTO plan_assignment (plan_item_id, plan_month_id, ott_service_id, minutes) VALUES (?, ?, 1, 60)",
                itemId, monthId);

        jdbc.update("DELETE FROM plan WHERE id = ?", planId);

        assertThat(count("plan_month")).isZero();
        assertThat(count("plan_month_product")).isZero();
        assertThat(count("plan_item")).isZero();
        assertThat(count("plan_assignment")).isZero();
    }

    @Test
    void 사용자를_지우면_소유한_행이_모두_사라지고_계산_기록은_플랜_연결만_끊긴다() {
        long userId = insertUser("b@test.com");
        long planId = insertPlan(userId, "ACTIVE", "RECOMMENDED");
        jdbc.update("INSERT INTO user_setting (user_id, monthly_budget, monthly_watch_minutes, watch_preset) VALUES (?, 30000, 1200, 'NORMAL')", userId);
        jdbc.update("INSERT INTO user_subscription (user_id, ott_service_id, status) VALUES (?, 1, 'FREE')", userId);
        jdbc.update("INSERT INTO user_price_override (user_id, product_id, monthly_price) VALUES (?, ?, 9900)", userId, insertProduct());
        jdbc.update("INSERT INTO wishlist_item (user_id, watch_unit_id, priority) VALUES (?, ?, 'MUST')", userId, insertWatchUnit(2002));
        jdbc.update("INSERT INTO notification_outbox (user_id, type, dedupe_key, payload) VALUES (?, 'MONTHLY', 'MONTHLY:b:2026-11', '{}')", userId);
        jdbc.update(
                "INSERT INTO calc_run (plan_id, input_hash, plan_type, algorithm, must_completed, score, total_cost, candidate_count, elapsed_ms) "
                        + "VALUES (?, repeat('a', 64), 'RECOMMENDED', 'EXACT', 1, 2, 10000, 5, 12)",
                planId);

        jdbc.update("DELETE FROM users WHERE id = ?", userId);

        assertThat(count("user_setting")).isZero();
        assertThat(count("user_subscription")).isZero();
        assertThat(count("user_price_override")).isZero();
        assertThat(count("wishlist_item")).isZero();
        assertThat(count("plan")).isZero();
        assertThat(count("notification_outbox")).isZero();
        Long calcPlanId = jdbc.queryForObject("SELECT plan_id FROM calc_run", Long.class);
        assertThat(count("calc_run")).isEqualTo(1);
        assertThat(calcPlanId).isNull();
    }

    @Test
    void 이전_플랜을_지워도_다음_플랜은_남고_연결만_끊긴다() {
        long userId = insertUser("c@test.com");
        long oldPlanId = insertPlan(userId, "ARCHIVED", "RECOMMENDED");
        jdbc.update("INSERT INTO plan (user_id, previous_plan_id, status, plan_type, start_month, monthly_budget, monthly_watch_minutes, "
                        + "algorithm, must_total, must_completed, score, total_cost, all_subscribe_cost, data_as_of, activated_at) "
                        + "VALUES (?, ?, 'ACTIVE', 'RECOMMENDED', DATE '2026-12-01', 30000, 1200, 'EXACT', 1, 1, 2, 10000, 20000, now(), now())",
                userId, oldPlanId);

        jdbc.update("DELETE FROM plan WHERE id = ?", oldPlanId);

        Long previous = jdbc.queryForObject("SELECT previous_plan_id FROM plan WHERE status = 'ACTIVE'", Long.class);
        assertThat(previous).isNull();
    }

    @Test
    void 한_사용자의_ACTIVE_플랜이_둘이면_거부한다() {
        long userId = insertUser("d@test.com");
        insertPlan(userId, "ACTIVE", "RECOMMENDED");

        assertThatThrownBy(() -> insertPlan(userId, "ACTIVE", "SAVER")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_유형_DRAFT가_둘이면_거부한다() {
        long userId = insertUser("e@test.com");
        insertPlan(userId, "DRAFT", "SAVER");

        assertThatThrownBy(() -> insertPlan(userId, "DRAFT", "SAVER")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 서로_다른_유형_DRAFT_3개는_허용한다() {
        long userId = insertUser("f@test.com");
        insertPlan(userId, "DRAFT", "RECOMMENDED");
        insertPlan(userId, "DRAFT", "SAVER");
        insertPlan(userId, "DRAFT", "SIMPLE");

        assertThat(count("plan")).isEqualTo(3);
    }

    @Test
    void 구독_중이_아닌데_결제일이_있으면_거부한다() {
        long userId = insertUser("g@test.com");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO user_subscription (user_id, ott_service_id, status, billing_day) VALUES (?, 1, 'FREE', 5)", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 구독_중이_아닌데_플랜명이_있으면_거부한다() {
        long userId = insertUser("g2@test.com");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO user_subscription (user_id, ott_service_id, status, plan_name) VALUES (?, 1, 'FREE', '스탠다드')", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 확정_여부가_달_번호와_다르면_거부한다() {
        long planId = insertPlan(insertUser("h@test.com"), "DRAFT", "RECOMMENDED");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO plan_month (plan_id, month_index, month, confirmed, cost) VALUES (?, 1, DATE '2026-12-01', TRUE, 0)", planId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 이번_달인데_확정이_아니면_거부한다() {
        long planId = insertPlan(insertUser("h2@test.com"), "DRAFT", "RECOMMENDED");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO plan_month (plan_id, month_index, month, confirmed, cost) VALUES (?, 0, DATE '2026-11-01', FALSE, 0)", planId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_dedupe_key_알림은_거부한다() {
        long userId = insertUser("i@test.com");
        String sql = "INSERT INTO notification_outbox (user_id, type, dedupe_key, payload) VALUES (?, 'MONTHLY', 'MONTHLY:i:2026-11', '{}')";
        jdbc.update(sql, userId);

        assertThatThrownBy(() -> jdbc.update(sql, userId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ShedLock_표가_있고_이름이_기본키다() {
        jdbc.update("INSERT INTO shedlock (name, lock_until, locked_at, locked_by) VALUES ('job', now(), now(), 'host')");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO shedlock (name, lock_until, locked_at, locked_by) VALUES ('job', now(), now(), 'host')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 참조_컬럼_인덱스가_모두_있다() {
        Integer indexes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname IN ('idx_plan_user', 'idx_plan_previous', "
                        + "'idx_plan_assignment_month', 'idx_calc_run_plan', 'idx_notification_outbox_user', 'idx_notification_outbox_due')",
                Integer.class);
        assertThat(indexes).isEqualTo(6);
    }
}

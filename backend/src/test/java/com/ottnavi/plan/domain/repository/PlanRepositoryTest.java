package com.ottnavi.plan.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.entity.PlanAssignment;
import com.ottnavi.plan.domain.entity.PlanItem;
import com.ottnavi.plan.domain.entity.PlanMonth;
import com.ottnavi.plan.domain.entity.PlanMonthProduct;
import com.ottnavi.product.domain.enums.PriceSource;
import com.ottnavi.support.IntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랜 엔티티 매핑·DRAFT 벌크 삭제·부분 유니크 테스트(Task 062). 컨텍스트가 뜨는 것 자체가 validate 통과다.
 * 테스트마다 롤백한다. PostgreSQL은 문장이 실패하면 그 트랜잭션을 더 쓸 수 없어 거부 테스트는 실패 문장을 하나만 둔다.
 */
@Transactional
class PlanRepositoryTest extends IntegrationTestSupport {

    @Autowired
    PlanRepository planRepository;
    @Autowired
    PlanMonthRepository planMonthRepository;
    @Autowired
    PlanMonthProductRepository planMonthProductRepository;
    @Autowired
    PlanItemRepository planItemRepository;
    @Autowired
    PlanAssignmentRepository planAssignmentRepository;
    @Autowired
    JdbcTemplate jdbc;

    private long insertUser() {
        return jdbc.queryForObject(
                "INSERT INTO users (email, name, oauth_provider, oauth_subject) VALUES ('plan@test.com', '테스트', 'GOOGLE', 'sub-plan') RETURNING id",
                Long.class);
    }

    private long insertWatchUnit() {
        long titleId = jdbc.queryForObject(
                "INSERT INTO title (media_type, tmdb_id, title, fetched_at) VALUES ('MOVIE', 9001, '테스트 작품', now()) RETURNING id",
                Long.class);
        return jdbc.queryForObject(
                "INSERT INTO watch_unit (title_id, unit_type, season_number, total_runtime_min, fetched_at) "
                        + "VALUES (?, 'MOVIE', NULL, 120, now()) RETURNING id",
                Long.class, titleId);
    }

    private long insertProduct() {
        return jdbc.queryForObject(
                "INSERT INTO subscription_product (name, product_type) VALUES ('테스트 상품', 'SINGLE') RETURNING id",
                Long.class);
    }

    private Plan newPlan(long userId, PlanType type) {
        return Plan.createDraft(userId, null, type, LocalDate.of(2026, 11, 1), 30000, 1200, SolverType.EXACT,
                1, 1, 2, 10000, 20000, Instant.now());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    void DRAFT를_벌크_삭제하면_하위_행이_사라지고_영속성_컨텍스트에서도_조회되지_않는다() {
        long userId = insertUser();
        Plan plan = planRepository.save(newPlan(userId, PlanType.RECOMMENDED));
        PlanMonth month = planMonthRepository.save(PlanMonth.create(plan, 0, LocalDate.of(2026, 11, 1), 5000));
        planMonthProductRepository.save(PlanMonthProduct.create(month, insertProduct(), 5000, PriceSource.ADMIN));
        PlanItem item = planItemRepository.save(PlanItem.scheduled(plan, insertWatchUnit(), Priority.MUST, 0));
        planAssignmentRepository.save(PlanAssignment.create(item, month, 1L, 60));
        Long planId = plan.getId();
        assertThat(count("plan_assignment")).isEqualTo(1); // 저장 쿼리가 나갔는지 확인(IDENTITY라 즉시 INSERT)

        int deleted = planRepository.deleteDraftsByUserId(userId);

        assertThat(deleted).isEqualTo(1);
        assertThat(count("plan")).isZero();
        assertThat(count("plan_month")).isZero();
        assertThat(count("plan_month_product")).isZero();
        assertThat(count("plan_item")).isZero();
        assertThat(count("plan_assignment")).isZero();
        assertThat(planRepository.findById(planId)).isEmpty(); // clearAutomatically로 1차 캐시가 비워졌다
    }

    @Test
    void ACTIVE_2개는_DB가_거부한다() {
        long userId = insertUser();
        Plan first = newPlan(userId, PlanType.RECOMMENDED);
        first.activate(Instant.now());
        planRepository.saveAndFlush(first);
        Plan second = newPlan(userId, PlanType.SAVER);
        second.activate(Instant.now());

        assertThatThrownBy(() -> planRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_유형_DRAFT_2개는_DB가_거부한다() {
        long userId = insertUser();
        planRepository.saveAndFlush(newPlan(userId, PlanType.SAVER));

        assertThatThrownBy(() -> planRepository.saveAndFlush(newPlan(userId, PlanType.SAVER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

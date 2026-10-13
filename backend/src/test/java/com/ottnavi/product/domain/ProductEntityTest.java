package com.ottnavi.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.provider.domain.OttService;
import com.ottnavi.provider.domain.OttServiceRepository;
import com.ottnavi.provider.domain.ProviderTier;
import com.ottnavi.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** 상품·구성·요금 매핑 테스트(Task 035). 복합 기본키 저장·조회를 포함한다. 테스트마다 롤백한다 */
@Transactional
class ProductEntityTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-10-13T01:02:03Z");

    @Autowired
    SubscriptionProductRepository subscriptionProductRepository;
    @Autowired
    ProductComponentRepository productComponentRepository;
    @Autowired
    ProductPriceRepository productPriceRepository;
    @Autowired
    OttServiceRepository ottServiceRepository;
    @Autowired
    EntityManager entityManager;

    @Test
    void 상품_구성은_복합_키로_저장되고_다시_읽힌다() {
        SubscriptionProduct product = subscriptionProductRepository.saveAndFlush(
                SubscriptionProduct.create("넷플릭스 스탠다드", ProductType.SINGLE));
        OttService service = ottServiceRepository.findAll().get(0);
        productComponentRepository.saveAndFlush(ProductComponent.create(product, service, ProviderTier.STANDARD));
        entityManager.clear();

        ProductComponent found = productComponentRepository
                .findById(new ProductComponentId(product.getId(), service.getId())).orElseThrow();

        assertThat(found.getTier()).isEqualTo(ProviderTier.STANDARD);
        assertThat(found.getProduct().getName()).isEqualTo("넷플릭스 스탠다드");
        assertThat(found.getOttService().getId()).isEqualTo(service.getId());
        assertThat(product.isActive()).isTrue(); // 새 상품은 판매 중으로 시작한다
    }

    @Test
    void 요금_이력이_저장되고_같은_시작일은_유니크_위반이다() {
        SubscriptionProduct product = subscriptionProductRepository.saveAndFlush(
                SubscriptionProduct.create("테스트 상품", ProductType.SINGLE));
        LocalDate from = LocalDate.of(2026, 10, 1);
        productPriceRepository.saveAndFlush(ProductPrice.create(product, 13500, from, NOW));
        entityManager.clear();

        assertThat(productPriceRepository.findAll()).singleElement()
                .satisfies(p -> {
                    assertThat(p.getMonthlyPrice()).isEqualTo(13500);
                    assertThat(p.getEffectiveFrom()).isEqualTo(from);
                    assertThat(p.getLastVerifiedAt()).isEqualTo(NOW);
                });
        SubscriptionProduct managed = subscriptionProductRepository.findById(product.getId()).orElseThrow();
        assertThatThrownBy(() -> productPriceRepository.saveAndFlush(ProductPrice.create(managed, 14500, from, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 음수_요금은_저장_전에_거부한다() {
        assertThatThrownBy(() -> ProductPrice.create(null, -1, LocalDate.of(2026, 10, 1), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

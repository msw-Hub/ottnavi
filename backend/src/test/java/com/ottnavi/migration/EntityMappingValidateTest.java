package com.ottnavi.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.support.IntegrationTestSupport;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 엔티티와 DB 구조 일치 테스트(Task 035). 컨텍스트가 뜨는 것 자체가 ddl-auto=validate 통과의 증거다:
 * 엔티티의 타입·컬럼 이름이 Flyway가 만든 테이블과 다르면 이 클래스의 모든 테스트가 기동 실패로 깨진다.
 */
class EntityMappingValidateTest extends IntegrationTestSupport {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void 작품과_상품_영역_엔티티가_모두_등록돼_validate를_통과한다() {
        Set<String> names = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(EntityType::getName)
                .collect(Collectors.toSet());

        assertThat(names).contains(
                "OttService", "TmdbProvider",
                "Title", "WatchUnit", "Availability", "AvailabilityChange", "Genre", "TitleGenre", "TitleCast",
                "SubscriptionProduct", "ProductComponent", "ProductPrice");
    }
}

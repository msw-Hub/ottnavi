package com.ottnavi.provider.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.provider.domain.entity.TmdbProvider;
import com.ottnavi.provider.domain.enums.MappingState;
import com.ottnavi.provider.domain.enums.OttServiceCode;
import com.ottnavi.provider.domain.enums.ProviderTier;
import com.ottnavi.support.IntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** TmdbProvider 매핑·리포지토리 테스트(Task 035). V2 시드 8행을 엔티티로 읽는다. 테스트마다 롤백한다 */
@Transactional
class TmdbProviderRepositoryTest extends IntegrationTestSupport {

    @Autowired
    TmdbProviderRepository tmdbProviderRepository;

    @Autowired
    OttServiceRepository ottServiceRepository;

    @Test
    void 시드_MAPPED_8행이_서비스와_함께_조회된다() {
        List<TmdbProvider> mapped = tmdbProviderRepository.findByMappingState(MappingState.MAPPED);

        assertThat(mapped).hasSize(8);
        assertThat(mapped).allSatisfy(p -> assertThat(p.getOttService()).isNotNull());
        TmdbProvider netflixAd = tmdbProviderRepository.findById(1796).orElseThrow();
        assertThat(netflixAd.getTier()).isEqualTo(ProviderTier.AD);
        assertThat(netflixAd.getOttService().getCode()).isEqualTo(OttServiceCode.NETFLIX);
    }

    @Test
    void 새로_발견한_제공처는_UNMAPPED로_저장되고_매핑하면_MAPPED가_된다() {
        tmdbProviderRepository.saveAndFlush(TmdbProvider.discover(99999, "테스트 제공처", null, Instant.now()));
        assertThat(tmdbProviderRepository.findByMappingState(MappingState.UNMAPPED)).hasSize(1);

        TmdbProvider provider = tmdbProviderRepository.findById(99999).orElseThrow();
        provider.mapTo(ottServiceRepository.findAll().get(0), ProviderTier.STANDARD);
        tmdbProviderRepository.flush(); // 더티 체킹으로 UPDATE가 나가고, DB CHECK(MAPPED이면 서비스 있음)도 통과한다

        assertThat(tmdbProviderRepository.findByMappingState(MappingState.MAPPED)).hasSize(9);
    }
}

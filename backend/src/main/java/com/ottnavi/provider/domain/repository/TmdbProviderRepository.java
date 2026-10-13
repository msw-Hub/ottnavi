package com.ottnavi.provider.domain.repository;

import com.ottnavi.provider.domain.entity.TmdbProvider;
import com.ottnavi.provider.domain.enums.MappingState;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TmdbProviderRepository extends JpaRepository<TmdbProvider, Integer> {

    /** 매핑 상태별 제공처를 조회한다. 판정에는 MAPPED만 쓴다 */
    List<TmdbProvider> findByMappingState(MappingState mappingState);

    /** MAPPED 제공처를 서비스와 함께 서비스 정렬 순서, 제공처 번호 순으로 조회한다. 수집이 서비스별 discover 대상을 만들 때 쓴다 */
    @Query("select p from TmdbProvider p join fetch p.ottService s "
            + "where p.mappingState = com.ottnavi.provider.domain.enums.MappingState.MAPPED "
            + "order by s.displayOrder, p.providerId")
    List<TmdbProvider> findMappedWithService();
}

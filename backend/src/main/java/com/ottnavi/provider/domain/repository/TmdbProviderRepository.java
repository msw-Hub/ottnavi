package com.ottnavi.provider.domain.repository;

import com.ottnavi.provider.domain.entity.TmdbProvider;
import com.ottnavi.provider.domain.enums.MappingState;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TmdbProviderRepository extends JpaRepository<TmdbProvider, Integer> {

    /** 매핑 상태별 제공처를 조회한다. 판정에는 MAPPED만 쓴다 */
    List<TmdbProvider> findByMappingState(MappingState mappingState);
}

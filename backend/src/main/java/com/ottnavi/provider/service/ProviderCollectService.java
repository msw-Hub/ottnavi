package com.ottnavi.provider.service;

import com.ottnavi.provider.domain.entity.TmdbProvider;
import com.ottnavi.provider.domain.enums.OttServiceCode;
import com.ottnavi.provider.domain.repository.TmdbProviderRepository;
import com.ottnavi.provider.service.model.DiscoverTarget;
import com.ottnavi.provider.service.model.DiscoveredProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수집 Job이 쓰는 제공처 서비스 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProviderCollectService {

    private final TmdbProviderRepository tmdbProviderRepository;

    /** 서비스별 discover 대상을 화면 정렬 순서대로 돌려준다. MAPPED 제공처 번호만 |(OR)로 묶는다 */
    public List<DiscoverTarget> findDiscoverTargets() {
        // 서비스 순서를 지키려고 LinkedHashMap을 쓴다(쿼리가 서비스 정렬 순서, 번호 순으로 돌려준다)
        Map<OttServiceCode, List<Integer>> idsByService = new LinkedHashMap<>();
        for (TmdbProvider provider : tmdbProviderRepository.findMappedWithService()) {
            idsByService.computeIfAbsent(provider.getOttService().getCode(), code -> new ArrayList<>())
                    .add(provider.getProviderId());
        }
        return idsByService.entrySet().stream()
                .map(entry -> new DiscoverTarget(entry.getKey(), entry.getValue().stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining("|"))))
                .toList();
    }

    /** 처음 보는 제공처만 UNMAPPED로 저장하고 새로 저장한 개수를 돌려준다. 이미 있는 행은 덮어쓰지 않는다 */
    @Transactional
    public int registerDiscovered(List<DiscoveredProvider> providers, Instant firstSeenAt) {
        Set<Integer> existingIds = tmdbProviderRepository.findAll().stream()
                .map(TmdbProvider::getProviderId)
                .collect(Collectors.toSet());
        int saved = 0;
        for (DiscoveredProvider provider : providers) {
            if (existingIds.contains(provider.providerId())) {
                continue; // MAPPED·IGNORED 같은 관리자 결정을 지우지 않는다
            }
            tmdbProviderRepository.save(
                    TmdbProvider.discover(provider.providerId(), provider.name(), provider.logoPath(), firstSeenAt));
            saved++;
        }
        return saved;
    }
}

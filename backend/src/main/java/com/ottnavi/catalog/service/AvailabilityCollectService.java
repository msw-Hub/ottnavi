package com.ottnavi.catalog.service;

import com.ottnavi.catalog.domain.entity.Availability;
import com.ottnavi.catalog.domain.entity.AvailabilityChange;
import com.ottnavi.catalog.domain.entity.WatchUnit;
import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.repository.AvailabilityChangeRepository;
import com.ottnavi.catalog.domain.repository.AvailabilityRepository;
import com.ottnavi.catalog.domain.repository.WatchUnitRepository;
import com.ottnavi.catalog.domain.rule.AvailabilityChangeDetector;
import com.ottnavi.catalog.domain.rule.AvailabilityJudge;
import com.ottnavi.catalog.domain.rule.AvailabilityVerdict;
import com.ottnavi.catalog.domain.rule.ProviderMapping;
import com.ottnavi.catalog.domain.rule.ServiceInfo;
import com.ottnavi.catalog.service.model.UnitProviders;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.service.OttServiceQueryService;
import com.ottnavi.provider.service.ProviderCollectService;
import com.ottnavi.provider.service.model.MappedProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수집 Job의 판정 Step이 쓰는 저장 서비스: 제공 상태를 판정해 availability를 맞추고 상태가 바뀐 것만 이력을 남긴다(Task 044) */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AvailabilityCollectService {

    private final WatchUnitRepository watchUnitRepository;
    private final AvailabilityRepository availabilityRepository;
    private final AvailabilityChangeRepository availabilityChangeRepository;
    private final OttServiceQueryService ottServiceQueryService;
    private final ProviderCollectService providerCollectService;

    /**
     * 시청 단위별 KR 제공처 결과를 판정해 서비스마다 availability를 만들거나 갱신하고, 상태(status)가 바뀐 경우만 이력을 쌓는다.
     * 제공 상태 변경과 이력은 같은 트랜잭션이다(둘 중 하나만 남는 일이 없다). 상태가 바뀐(또는 새로 생긴) 건수를 돌려준다.
     *
     * @param jobExecutionId 이력에 남길 Spring Batch 실행 ID
     */
    @Transactional
    public int apply(List<UnitProviders> items, Long jobExecutionId, Instant checkedAt) {
        if (items.isEmpty()) {
            return 0;
        }
        List<OttService> services = ottServiceQueryService.listOttServices();
        List<ServiceInfo> serviceInfos = new ArrayList<>();
        Map<Long, OttService> serviceById = new HashMap<>();
        for (OttService service : services) {
            serviceInfos.add(new ServiceInfo(service.getId(), service.getCode()));
            serviceById.put(service.getId(), service);
        }
        // MAPPED 제공처만 판정에 쓴다(UNMAPPED·IGNORED는 없는 것으로 본다)
        Map<Integer, ProviderMapping> mappings = new HashMap<>();
        for (MappedProvider provider : providerCollectService.findMappedProviders().values()) {
            mappings.put(provider.providerId(), new ProviderMapping(provider.ottServiceId(), provider.tier()));
        }

        List<Long> unitIds = new ArrayList<>();
        for (UnitProviders item : items) {
            unitIds.add(item.watchUnitId());
        }
        Map<Long, WatchUnit> units = new HashMap<>();
        for (WatchUnit unit : watchUnitRepository.findAllById(unitIds)) {
            units.put(unit.getId(), unit);
        }
        // (시청 단위, 서비스) → 기존 제공 상태. chunk마다 한 번에 읽어 단위별 쿼리를 피한다
        Map<String, Availability> existing = new HashMap<>();
        for (Availability availability : availabilityRepository.findByWatchUnitIdIn(unitIds)) {
            existing.put(key(availability.getWatchUnit().getId(), availability.getOttService().getId()), availability);
        }

        int changed = 0;
        for (UnitProviders item : items) {
            WatchUnit unit = units.get(item.watchUnitId());
            if (unit == null) {
                log.warn("판정할 시청 단위가 DB에 없어 건너뛴다: watchUnitId={}", item.watchUnitId());
                continue;
            }
            for (AvailabilityVerdict verdict : AvailabilityJudge.judge(unit.getUnitType(), item.kr(), serviceInfos, mappings)) {
                OttService service = serviceById.get(verdict.ottServiceId());
                Availability current = existing.get(key(unit.getId(), service.getId()));
                AvailabilityStatus before = null; // 기존 행이 없으면 null(처음 생김)
                if (current != null) {
                    before = current.getStatus();
                }
                if (current == null) {
                    availabilityRepository.save(Availability.create(unit, service, verdict.status(),
                            verdict.unknownReason(), verdict.inStandard(), verdict.inAdTier(), checkedAt));
                } else {
                    // 영속 엔티티라 더티 체킹으로 UPDATE된다. 값이 그대로여도 checkedAt은 바뀌어 확인 시각이 남는다
                    current.changeState(verdict.status(), verdict.unknownReason(), verdict.inStandard(),
                            verdict.inAdTier(), checkedAt);
                }
                // 이력은 status가 바뀌었거나 처음 생긴 경우만. 이유·tier 플래그만 바뀐 것은 남기지 않는다
                if (AvailabilityChangeDetector.detect(before, verdict.status()).isPresent()) {
                    availabilityChangeRepository.save(AvailabilityChange.create(unit, service, before,
                            verdict.status(), jobExecutionId, checkedAt));
                    changed++;
                }
            }
        }
        return changed;
    }

    private static String key(long watchUnitId, long ottServiceId) {
        return watchUnitId + ":" + ottServiceId;
    }
}

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
        // 1. 처리할 단위가 없으면 바로 끝낸다
        if (items.isEmpty()) {
            return 0;
        }
        // 2. 판정 대상 서비스 목록과 ID 조회표를 만든다
        List<OttService> services = ottServiceQueryService.listOttServices();
        List<ServiceInfo> serviceInfos = new ArrayList<>();
        Map<Long, OttService> serviceById = new HashMap<>();
        for (OttService service : services) {
            serviceInfos.add(new ServiceInfo(service.getId(), service.getCode()));
            serviceById.put(service.getId(), service);
        }
        // 3. 제공처 번호 → 우리 서비스·tier 매핑을 만든다
        // MAPPED 제공처만 판정에 쓴다(UNMAPPED·IGNORED는 없는 것으로 본다)
        Map<Integer, ProviderMapping> mappings = new HashMap<>();
        for (MappedProvider provider : providerCollectService.findMappedProviders().values()) {
            mappings.put(provider.providerId(), new ProviderMapping(provider.ottServiceId(), provider.tier()));
        }

        // 4. 이번 chunk의 시청 단위를 한 번에 읽어 온다
        List<Long> unitIds = new ArrayList<>();
        for (UnitProviders item : items) {
            unitIds.add(item.watchUnitId());
        }
        Map<Long, WatchUnit> units = new HashMap<>();
        for (WatchUnit unit : watchUnitRepository.findAllById(unitIds)) {
            units.put(unit.getId(), unit);
        }
        // 5. 기존 제공 상태를 한 번에 읽어 단위별 쿼리를 피한다
        // (시청 단위, 서비스) → 기존 제공 상태. chunk마다 한 번에 읽어 단위별 쿼리를 피한다
        Map<String, Availability> existing = new HashMap<>();
        for (Availability availability : availabilityRepository.findByWatchUnitIdIn(unitIds)) {
            existing.put(key(availability.getWatchUnit().getId(), availability.getOttService().getId()), availability);
        }

        // 6. 단위마다 서비스 전체를 판정해 저장하고, 상태가 바뀐 것만 이력을 남긴다
        int changed = 0;
        for (UnitProviders item : items) {
            WatchUnit unit = units.get(item.watchUnitId());
            // 7. DB에 없는 시청 단위는 경고만 남기고 건너뛴다
            if (unit == null) {
                log.warn("판정할 시청 단위가 DB에 없어 건너뛴다: watchUnitId={}", item.watchUnitId());
                continue;
            }
            // 8. 서비스별 판정 결과를 기존 상태와 맞춘다
            for (AvailabilityVerdict verdict : AvailabilityJudge.judge(unit.getUnitType(), item.kr(), serviceInfos, mappings)) {
                OttService service = serviceById.get(verdict.ottServiceId());
                Availability current = existing.get(key(unit.getId(), service.getId()));
                AvailabilityStatus before = null; // 기존 행이 없으면 null(처음 생김)
                if (current != null) {
                    before = current.getStatus();
                }
                // 9. 새로 생긴 행은 저장하고, 기존 행은 값을 갱신한다
                if (current == null) {
                    availabilityRepository.save(Availability.create(unit, service, verdict.status(),
                            verdict.unknownReason(), verdict.inStandard(), verdict.inAdTier(), checkedAt));
                } else {
                    // 영속 엔티티라 더티 체킹으로 UPDATE된다. 값이 그대로여도 checkedAt은 바뀌어 확인 시각이 남는다
                    current.changeState(verdict.status(), verdict.unknownReason(), verdict.inStandard(),
                            verdict.inAdTier(), checkedAt);
                }
                // 10. 상태가 바뀌었거나 처음 생긴 경우만 이력을 남기고 건수를 센다
                // 이력은 status가 바뀌었거나 처음 생긴 경우만. 이유·tier 플래그만 바뀐 것은 남기지 않는다
                if (AvailabilityChangeDetector.detect(before, verdict.status()).isPresent()) {
                    availabilityChangeRepository.save(AvailabilityChange.create(unit, service, before,
                            verdict.status(), jobExecutionId, checkedAt));
                    changed++;
                }
            }
        }
        // 11. 상태가 바뀐(새로 생긴) 건수를 돌려준다
        return changed;
    }

    private static String key(long watchUnitId, long ottServiceId) {
        return watchUnitId + ":" + ottServiceId;
    }
}

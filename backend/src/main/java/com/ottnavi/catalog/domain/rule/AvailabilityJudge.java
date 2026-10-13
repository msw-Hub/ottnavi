package com.ottnavi.catalog.domain.rule;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.UnitType;
import com.ottnavi.catalog.domain.enums.UnknownReason;
import com.ottnavi.provider.domain.enums.OttServiceCode;
import com.ottnavi.provider.domain.enums.ProviderTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 제공 상태 3상태 판정(PRD FR-02, 8.2). 순수 Java라 Spring·DB·TMDB 없이 호출하고 테스트한다 */
public final class AvailabilityJudge {

    private AvailabilityJudge() {
    }

    /**
     * 시청 단위 하나에 대해 서비스 전체의 판정을 돌려준다(입력 services와 같은 순서).
     * 순서: ① 쿠팡플레이 영화는 STRUCTURAL_GAP ② KR 데이터가 없으면 NO_KR_DATA ③ flatrate에 직접 있으면 AVAILABLE, 아니면 NOT_AVAILABLE.
     *
     * @param unitType 시청 단위 종류(영화 단위인지 시즌 단위인지)
     * @param kr 그 단위의 KR 제공처 조회 결과
     * @param services 판정할 서비스 목록(보통 7곳)
     * @param mappings 제공처 번호 → 우리 서비스·tier. MAPPED인 제공처만 넣는다
     */
    public static List<AvailabilityVerdict> judge(UnitType unitType, KrProviders kr,
                                                  List<ServiceInfo> services, Map<Integer, ProviderMapping> mappings) {
        List<AvailabilityVerdict> verdicts = new ArrayList<>();
        for (ServiceInfo service : services) {
            verdicts.add(judgeOne(unitType, kr, service, mappings));
        }
        return verdicts;
    }

    private static AvailabilityVerdict judgeOne(UnitType unitType, KrProviders kr, ServiceInfo service,
                                                Map<Integer, ProviderMapping> mappings) {
        long serviceId = service.ottServiceId();

        // 쿠팡플레이는 TMDB에 영화 데이터가 구조적으로 없어, KR 데이터 유무와 상관없이 가장 먼저 모름으로 둔다(PRD 11절 13번)
        if (unitType == UnitType.MOVIE && service.code() == OttServiceCode.COUPANG_PLAY) {
            return new AvailabilityVerdict(serviceId, AvailabilityStatus.UNKNOWN, UnknownReason.STRUCTURAL_GAP, false, false);
        }
        if (!kr.krDataPresent()) {
            return new AvailabilityVerdict(serviceId, AvailabilityStatus.UNKNOWN, UnknownReason.NO_KR_DATA, false, false);
        }

        // flatrate(구독)에 이 서비스로 매핑된 제공처가 직접 있는지만 본다. 대여·구매와 매핑되지 않은 번호는 무시한다
        boolean inStandard = false;
        boolean inAdTier = false;
        for (Integer providerId : kr.flatrateProviderIds()) {
            ProviderMapping mapping = mappings.get(providerId);
            if (mapping == null || mapping.ottServiceId() != serviceId) {
                continue;
            }
            if (mapping.tier() == ProviderTier.AD) {
                inAdTier = true;
            } else {
                inStandard = true;
            }
        }
        // AVAILABLE이면 inStandard·inAdTier 중 하나는 반드시 true다(Task 044 이월 규칙 ①)
        AvailabilityStatus status = (inStandard || inAdTier) ? AvailabilityStatus.AVAILABLE : AvailabilityStatus.NOT_AVAILABLE;
        return new AvailabilityVerdict(serviceId, status, null, inStandard, inAdTier);
    }
}

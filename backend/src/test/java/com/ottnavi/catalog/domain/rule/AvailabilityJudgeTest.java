package com.ottnavi.catalog.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.UnitType;
import com.ottnavi.catalog.domain.enums.UnknownReason;
import com.ottnavi.provider.domain.enums.OttServiceCode;
import com.ottnavi.provider.domain.enums.ProviderTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 제공 상태 판정 단위 테스트(Task 042). Spring 없이 PRD FR-02·8.2의 규칙을 입력별로 확인한다 */
class AvailabilityJudgeTest {

    // 서비스 7곳. ott_service.id는 코드 순서대로 1~7이라고 가정한다
    private static final List<ServiceInfo> SERVICES = serviceList();

    // V2 시드와 같은 매핑: 8·1796=넷플릭스(1796은 광고형), 337=디즈니+, 350=애플TV+, 1883=티빙, 356=웨이브, 97=왓챠, 1881=쿠팡플레이
    private static final Map<Integer, ProviderMapping> MAPPINGS = Map.of(
            8, new ProviderMapping(1, ProviderTier.STANDARD),
            1796, new ProviderMapping(1, ProviderTier.AD),
            337, new ProviderMapping(2, ProviderTier.STANDARD),
            350, new ProviderMapping(3, ProviderTier.STANDARD),
            1883, new ProviderMapping(4, ProviderTier.STANDARD),
            356, new ProviderMapping(5, ProviderTier.STANDARD),
            97, new ProviderMapping(6, ProviderTier.STANDARD),
            1881, new ProviderMapping(7, ProviderTier.STANDARD));

    private static List<ServiceInfo> serviceList() {
        List<ServiceInfo> list = new ArrayList<>();
        for (OttServiceCode code : OttServiceCode.values()) {
            list.add(new ServiceInfo(code.ordinal() + 1L, code));
        }
        return list;
    }

    // 서비스 id(1~7)에 해당하는 판정을 꺼낸다
    private static AvailabilityVerdict of(List<AvailabilityVerdict> verdicts, OttServiceCode code) {
        return verdicts.get(code.ordinal());
    }

    @Test
    void 웨이브가_구매만_있고_구독은_넷플릭스뿐이면_웨이브는_없음이다() {
        // KR flatrate에 웨이브(356)는 없고 넷플릭스(8)만 있다. 웨이브의 대여·구매는 입력에 아예 들어오지 않는다
        List<AvailabilityVerdict> verdicts = AvailabilityJudge.judge(
                UnitType.MOVIE, KrProviders.of(Set.of(8)), SERVICES, MAPPINGS);

        assertThat(of(verdicts, OttServiceCode.WAVVE).status()).isEqualTo(AvailabilityStatus.NOT_AVAILABLE);
        assertThat(of(verdicts, OttServiceCode.NETFLIX).status()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void 시즌마다_제공처가_다르면_시즌별로_따로_판정한다() {
        // 랑야방 구조: 시즌 1은 웨이브·티빙, 시즌 2는 넷플릭스·티빙
        List<AvailabilityVerdict> season1 = AvailabilityJudge.judge(
                UnitType.SEASON, KrProviders.of(Set.of(356, 1883)), SERVICES, MAPPINGS);
        List<AvailabilityVerdict> season2 = AvailabilityJudge.judge(
                UnitType.SEASON, KrProviders.of(Set.of(8, 1883)), SERVICES, MAPPINGS);

        assertThat(of(season1, OttServiceCode.WAVVE).status()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(of(season1, OttServiceCode.NETFLIX).status()).isEqualTo(AvailabilityStatus.NOT_AVAILABLE);
        assertThat(of(season2, OttServiceCode.WAVVE).status()).isEqualTo(AvailabilityStatus.NOT_AVAILABLE);
        assertThat(of(season2, OttServiceCode.NETFLIX).status()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(of(season1, OttServiceCode.TVING).status()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(of(season2, OttServiceCode.TVING).status()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void KR_데이터가_없으면_모든_서비스가_모름이고_이유는_KR_없음이다() {
        List<AvailabilityVerdict> verdicts = AvailabilityJudge.judge(
                UnitType.SEASON, KrProviders.noKrData(), SERVICES, MAPPINGS);

        assertThat(verdicts).hasSize(7).allSatisfy(verdict -> {
            assertThat(verdict.status()).isEqualTo(AvailabilityStatus.UNKNOWN);
            assertThat(verdict.unknownReason()).isEqualTo(UnknownReason.NO_KR_DATA);
            assertThat(verdict.inStandard()).isFalse();
            assertThat(verdict.inAdTier()).isFalse();
        });
    }

    @Test
    void KR은_있는데_구독_목록이_비면_모든_서비스가_없음이다() {
        List<AvailabilityVerdict> verdicts = AvailabilityJudge.judge(
                UnitType.SEASON, KrProviders.of(Set.of()), SERVICES, MAPPINGS);

        assertThat(verdicts).allSatisfy(verdict -> {
            assertThat(verdict.status()).isEqualTo(AvailabilityStatus.NOT_AVAILABLE);
            assertThat(verdict.unknownReason()).isNull();
        });
    }

    @Test
    void 구독_목록이_null이어도_빈_목록으로_본다() {
        List<AvailabilityVerdict> verdicts = AvailabilityJudge.judge(
                UnitType.MOVIE, new KrProviders(true, null), SERVICES, MAPPINGS);

        assertThat(of(verdicts, OttServiceCode.NETFLIX).status()).isEqualTo(AvailabilityStatus.NOT_AVAILABLE);
    }

    @Test
    void 쿠팡플레이_영화는_구독_목록과_상관없이_구조적_공백으로_모름이다() {
        List<AvailabilityVerdict> withKr = AvailabilityJudge.judge(
                UnitType.MOVIE, KrProviders.of(Set.of(1881, 8)), SERVICES, MAPPINGS);
        List<AvailabilityVerdict> withoutKr = AvailabilityJudge.judge(
                UnitType.MOVIE, KrProviders.noKrData(), SERVICES, MAPPINGS);

        assertThat(of(withKr, OttServiceCode.COUPANG_PLAY).status()).isEqualTo(AvailabilityStatus.UNKNOWN);
        assertThat(of(withKr, OttServiceCode.COUPANG_PLAY).unknownReason()).isEqualTo(UnknownReason.STRUCTURAL_GAP);
        assertThat(of(withoutKr, OttServiceCode.COUPANG_PLAY).unknownReason()).isEqualTo(UnknownReason.STRUCTURAL_GAP);
        // 다른 서비스는 영향이 없다
        assertThat(of(withKr, OttServiceCode.NETFLIX).status()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(of(withoutKr, OttServiceCode.NETFLIX).unknownReason()).isEqualTo(UnknownReason.NO_KR_DATA);
    }

    @Test
    void 쿠팡플레이_드라마는_구조적_공백이_아니라_일반_규칙을_따른다() {
        List<AvailabilityVerdict> available = AvailabilityJudge.judge(
                UnitType.SEASON, KrProviders.of(Set.of(1881)), SERVICES, MAPPINGS);
        List<AvailabilityVerdict> notAvailable = AvailabilityJudge.judge(
                UnitType.SEASON, KrProviders.of(Set.of(8)), SERVICES, MAPPINGS);

        assertThat(of(available, OttServiceCode.COUPANG_PLAY).status()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(of(notAvailable, OttServiceCode.COUPANG_PLAY).status()).isEqualTo(AvailabilityStatus.NOT_AVAILABLE);
    }

    @Test
    void 광고형_제공처만_있으면_있음이고_광고형_플래그만_켜진다() {
        List<AvailabilityVerdict> verdicts = AvailabilityJudge.judge(
                UnitType.MOVIE, KrProviders.of(Set.of(1796)), SERVICES, MAPPINGS);

        AvailabilityVerdict netflix = of(verdicts, OttServiceCode.NETFLIX);
        assertThat(netflix.status()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(netflix.inAdTier()).isTrue();
        assertThat(netflix.inStandard()).isFalse();
    }

    @Test
    void 일반과_광고형이_함께_있으면_두_플래그가_모두_켜진다() {
        List<AvailabilityVerdict> verdicts = AvailabilityJudge.judge(
                UnitType.MOVIE, KrProviders.of(Set.of(8, 1796)), SERVICES, MAPPINGS);

        AvailabilityVerdict netflix = of(verdicts, OttServiceCode.NETFLIX);
        assertThat(netflix.inStandard()).isTrue();
        assertThat(netflix.inAdTier()).isTrue();
    }

    @Test
    void 매핑되지_않은_제공처_번호는_무시한다() {
        // 9999는 매핑표에 없다. 다른 서비스를 있음으로 만들거나 예외를 내면 안 된다
        List<AvailabilityVerdict> verdicts = AvailabilityJudge.judge(
                UnitType.MOVIE, KrProviders.of(Set.of(9999)), SERVICES, MAPPINGS);

        assertThat(verdicts).allSatisfy(verdict ->
                assertThat(verdict.status()).isIn(AvailabilityStatus.NOT_AVAILABLE, AvailabilityStatus.UNKNOWN));
        assertThat(of(verdicts, OttServiceCode.NETFLIX).status()).isEqualTo(AvailabilityStatus.NOT_AVAILABLE);
    }

    @Test
    void 결과는_입력_서비스_순서대로_서비스마다_하나씩이고_있음이면_tier_플래그가_하나_이상_켜진다() {
        List<AvailabilityVerdict> verdicts = AvailabilityJudge.judge(
                UnitType.SEASON, KrProviders.of(Set.of(8, 337, 1883)), SERVICES, MAPPINGS);

        assertThat(verdicts).extracting(AvailabilityVerdict::ottServiceId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(verdicts).filteredOn(v -> v.status() == AvailabilityStatus.AVAILABLE)
                .hasSize(3)
                .allSatisfy(v -> assertThat(v.inStandard() || v.inAdTier()).isTrue());
    }
}

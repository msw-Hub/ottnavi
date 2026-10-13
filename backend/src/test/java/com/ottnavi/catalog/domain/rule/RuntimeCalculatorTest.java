package com.ottnavi.catalog.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 러닝타임 계산 단위 테스트(Task 042). 기본값은 PRD 5.4와 같은 120/45/24분을 쓴다 */
class RuntimeCalculatorTest {

    private static final RuntimeDefaults DEFAULTS = new RuntimeDefaults(120, 45, 24);
    private static final LocalDate TODAY = LocalDate.of(2023, 3, 10);

    private static EpisodeRuntime episode(String airDate, Integer runtime) {
        return new EpisodeRuntime(TitleDetailRules.parseDate(airDate), runtime);
    }

    @Test
    void 영화는_runtime_값을_그대로_쓰고_추정이_아니다() {
        RuntimeResult result = RuntimeCalculator.forMovie(98, DEFAULTS);

        assertThat(result).isEqualTo(new RuntimeResult(1, 1, 98, 0, false));
    }

    @Test
    void 영화_runtime이_비거나_0이면_120분으로_추정한다() {
        assertThat(RuntimeCalculator.forMovie(null, DEFAULTS)).isEqualTo(new RuntimeResult(1, 1, 120, 1, true));
        assertThat(RuntimeCalculator.forMovie(0, DEFAULTS)).isEqualTo(new RuntimeResult(1, 1, 120, 1, true));
    }

    @Test
    void 시즌은_방영된_회차의_runtime을_더한다() {
        List<EpisodeRuntime> episodes = List.of(
                episode("2023-03-01", 62), episode("2023-03-02", 58), episode("2023-03-08", 60));

        RuntimeResult result = RuntimeCalculator.forSeason(episodes, TODAY, false, DEFAULTS);

        assertThat(result).isEqualTo(new RuntimeResult(3, 3, 180, 0, false));
    }

    @Test
    void 방영된_회차의_runtime이_비면_45분으로_채우고_추정으로_표시한다() {
        // 테스트 샘플 tv-season.json과 같은 모양: 62, 58, 비어 있음(방영됨), 비어 있음(미방영)
        List<EpisodeRuntime> episodes = List.of(
                episode("2023-03-01", 62), episode("2023-03-02", 58), episode("2023-03-08", null), episode("", null));

        RuntimeResult result = RuntimeCalculator.forSeason(episodes, TODAY, false, DEFAULTS);

        assertThat(result.episodeCount()).isEqualTo(4);
        assertThat(result.airedEpisodeCount()).isEqualTo(3);
        assertThat(result.totalRuntimeMin()).isEqualTo(62 + 58 + 45);
        assertThat(result.runtimeMissingCount()).isEqualTo(1);
        assertThat(result.runtimeEstimated()).isTrue();
    }

    @Test
    void 애니메이션_시즌은_빈_회차를_24분으로_채운다() {
        List<EpisodeRuntime> episodes = List.of(episode("2023-03-01", null), episode("2023-03-02", null));

        RuntimeResult result = RuntimeCalculator.forSeason(episodes, TODAY, true, DEFAULTS);

        assertThat(result.totalRuntimeMin()).isEqualTo(48);
        assertThat(result.runtimeMissingCount()).isEqualTo(2);
        assertThat(result.runtimeEstimated()).isTrue();
    }

    @Test
    void 방영되지_않은_회차는_합계와_누락_수에서_뺀다() {
        List<EpisodeRuntime> episodes = List.of(
                episode("2023-03-01", 50), episode("2023-03-11", 50), episode("2023-04-01", null), episode(null, 50));

        RuntimeResult result = RuntimeCalculator.forSeason(episodes, TODAY, false, DEFAULTS);

        assertThat(result.airedEpisodeCount()).isEqualTo(1);
        assertThat(result.totalRuntimeMin()).isEqualTo(50);
        assertThat(result.runtimeMissingCount()).isZero();
        assertThat(result.runtimeEstimated()).isFalse();
    }

    @Test
    void 오늘_방영된_회차는_방영된_것으로_센다() {
        RuntimeResult result = RuntimeCalculator.forSeason(List.of(episode("2023-03-10", 40)), TODAY, false, DEFAULTS);

        assertThat(result.airedEpisodeCount()).isEqualTo(1);
        assertThat(result.totalRuntimeMin()).isEqualTo(40);
    }

    @Test
    void 회차_runtime이_0이면_빈_값으로_본다() {
        RuntimeResult result = RuntimeCalculator.forSeason(List.of(episode("2023-03-01", 0)), TODAY, false, DEFAULTS);

        assertThat(result.totalRuntimeMin()).isEqualTo(45);
        assertThat(result.runtimeMissingCount()).isEqualTo(1);
    }

    @Test
    void 회차_목록이_비어_있으면_모두_0이고_추정이_아니다() {
        RuntimeResult result = RuntimeCalculator.forSeason(List.of(), TODAY, false, DEFAULTS);

        assertThat(result).isEqualTo(new RuntimeResult(0, 0, 0, 0, false));
    }

    @Test
    void 애니메이션_장르는_16이_들어_있을_때만_애니메이션이다() {
        assertThat(RuntimeCalculator.isAnimation(Set.of(18, 16))).isTrue();
        assertThat(RuntimeCalculator.isAnimation(Set.of(18, 53))).isFalse();
        assertThat(RuntimeCalculator.isAnimation(Set.of())).isFalse();
        assertThat(RuntimeCalculator.isAnimation(null)).isFalse();
    }

    @Test
    void 애니메이션_영화여도_영화_기본값을_쓴다() {
        // PRD 5.4: 24분은 회차 기본값이다. forMovie는 장르를 받지 않으므로 120분이다
        assertThat(RuntimeCalculator.forMovie(null, DEFAULTS).totalRuntimeMin()).isEqualTo(120);
    }

    @Test
    void 기본값이_0_이하이면_만들_수_없다() {
        assertThatThrownBy(() -> new RuntimeDefaults(0, 45, 24)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuntimeDefaults(120, -1, 24)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuntimeDefaults(120, 45, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}

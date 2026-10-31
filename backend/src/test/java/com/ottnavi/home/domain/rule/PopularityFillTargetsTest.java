package com.ottnavi.home.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.service.model.TitleKey;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 인기 순위에서 상세를 채울 작품을 고르는 규칙을 DB 없이 확인한다.
 * 막으려는 사고: 같은 작품을 두 번 받는 일, 이미 있는 작품을 덮어쓰는 일, 상한을 넘겨 TMDB를 과하게 부르는 일, 순위 우선순위가 뒤섞이는 일.
 */
class PopularityFillTargetsTest {

	private PopularFillTarget target(MediaType mediaType, int tmdbId) {
		return new PopularFillTarget(mediaType, tmdbId, "작품" + tmdbId);
	}

	@Test
	void 여러_순위에_같은_작품이_나오면_한_번만_대상이_된다() {
		// given: 순위 A와 B에 드라마 2가 모두 있다. 영화 2는 같은 번호지만 다른 작품이다
		List<List<PopularFillTarget>> rankings = List.of(
				List.of(target(MediaType.MOVIE, 1), target(MediaType.TV, 2)),
				List.of(target(MediaType.TV, 2), target(MediaType.MOVIE, 2)));

		// when
		List<PopularFillTarget> picked = PopularityFillTargets.pick(rankings, Set.of(), 10);

		// then
		assertThat(picked).extracting(PopularFillTarget::tmdbId).containsExactly(1, 2, 2);
		assertThat(picked.get(1).mediaType()).isEqualTo(MediaType.TV);
		assertThat(picked.get(2).mediaType()).isEqualTo(MediaType.MOVIE);
	}

	@Test
	void 이미_DB에_있는_작품은_빠진다() {
		// given
		List<List<PopularFillTarget>> rankings = List.of(
				List.of(target(MediaType.MOVIE, 1), target(MediaType.MOVIE, 2), target(MediaType.TV, 1)));
		Set<TitleKey> existing = Set.of(new TitleKey(MediaType.MOVIE, 1));

		// when
		List<PopularFillTarget> picked = PopularityFillTargets.pick(rankings, existing, 10);

		// then: 영화 1만 빠지고 같은 번호의 드라마 1은 남는다
		assertThat(picked).extracting(PopularFillTarget::mediaType).containsExactly(MediaType.MOVIE, MediaType.TV);
		assertThat(picked).extracting(PopularFillTarget::tmdbId).containsExactly(2, 1);
	}

	@Test
	void 먼저_나온_순위의_순서를_지키고_상한에서_자른다() {
		// given: 첫 순위의 3개가 뒤 순위보다 앞선다
		List<List<PopularFillTarget>> rankings = List.of(
				List.of(target(MediaType.MOVIE, 10), target(MediaType.MOVIE, 11), target(MediaType.MOVIE, 12)),
				List.of(target(MediaType.MOVIE, 20), target(MediaType.MOVIE, 10)));

		// when
		List<PopularFillTarget> picked = PopularityFillTargets.pick(rankings, Set.of(), 4);

		// then
		assertThat(picked).extracting(PopularFillTarget::tmdbId).containsExactly(10, 11, 12, 20);
	}

	@Test
	void 순위가_모두_비면_빈_목록이다() {
		// given
		List<List<PopularFillTarget>> rankings = List.of(List.of(), List.of());

		// when
		List<PopularFillTarget> picked = PopularityFillTargets.pick(rankings, Set.of(), 20);

		// then
		assertThat(picked).isEmpty();
	}
}

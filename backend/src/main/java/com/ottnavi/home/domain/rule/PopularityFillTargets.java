package com.ottnavi.home.domain.rule;

import com.ottnavi.catalog.service.model.TitleKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 인기 순위들에서 "title 표에 없어 상세를 채울 작품"을 고르는 순수 규칙(DB·TMDB를 부르지 않는다).
 * 예) 순위 A=[영화1, 드라마2], 순위 B=[드라마2, 영화3], 이미 있는 키={영화1}, 상한 2 → [드라마2, 영화3].
 */
public final class PopularityFillTargets {

	private PopularityFillTargets() {
	}

	/**
	 * 순위 목록들을 앞에서부터 훑어 같은 작품은 한 번만, 이미 있는 작품은 빼고, 상한까지만 모아 돌려준다.
	 *
	 * @param rankings     순위 목록들(앞쪽 순위가 우선). 각 목록은 1위부터 순서대로다
	 * @param existingKeys 이미 title 표에 있는 작품 키
	 * @param maxTitles    돌려줄 최대 작품 수
	 */
	public static List<PopularFillTarget> pick(List<List<PopularFillTarget>> rankings, Set<TitleKey> existingKeys,
			int maxTitles) {
		// 1. 고른 결과와, 이미 본 작품 키(먼저 나온 순서를 지키며 중복을 거른다)
		List<PopularFillTarget> picked = new ArrayList<>();
		Set<TitleKey> seen = new HashSet<>();

		// 2. 순위 목록을 앞에서부터, 각 목록은 1위부터 훑는다
		for (List<PopularFillTarget> ranking : rankings) {
			for (PopularFillTarget candidate : ranking) {
				// 3. 상한에 닿으면 바로 끝낸다
				if (picked.size() >= maxTitles) {
					return picked;
				}
				// 4. 이미 본 작품이거나 title 표에 이미 있는 작품은 건너뛴다
				TitleKey key = new TitleKey(candidate.mediaType(), candidate.tmdbId());
				if (existingKeys.contains(key) || !seen.add(key)) {
					continue;
				}
				picked.add(candidate);
			}
		}
		return picked;
	}
}

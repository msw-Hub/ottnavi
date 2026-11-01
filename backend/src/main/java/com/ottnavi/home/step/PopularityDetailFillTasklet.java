package com.ottnavi.home.step;

import com.ottnavi.catalog.service.TitleCollectService;
import com.ottnavi.catalog.service.TitleFillService;
import com.ottnavi.catalog.service.model.FetchedTitle;
import com.ottnavi.catalog.service.model.TitleKey;
import com.ottnavi.collect.step.detail.SingleTitleFetcher;
import com.ottnavi.global.common.ExceptionRootCause;
import com.ottnavi.home.domain.rule.PopularFillTarget;
import com.ottnavi.home.domain.rule.PopularityFillTargets;
import com.ottnavi.home.job.PopularityFillProperties;
import com.ottnavi.home.service.PopularityRankingService;
import com.ottnavi.infra.tmdb.exception.TmdbException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 인기 수집 Step 2: 홈 인기 순위 상위 작품 중 title 표에 없는 작품의 상세를 TMDB에서 받아 채운다.
 * 순위에는 보이는데 작품 상세 API가 404인 문제를 막으려는 것이다. 쓴 수는 새로 채운 작품 수다.
 * TMDB 호출(fetch)은 트랜잭션 밖에서 끝내고, 저장(saveFetched)만 작품 하나당 트랜잭션 하나로 한다
 * (Tasklet에 트랜잭션 관리자를 주지 않는 것이 이 Job의 방식이다).
 * 작품 하나가 실패해도 그 작품만 건너뛴다. 이 Step이 실패해도 Job은 삭제 Step으로 넘어간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PopularityDetailFillTasklet implements Tasklet {

	private final PopularityFillProperties properties;
	private final PopularityRankingService popularityRankingService;
	private final TitleCollectService titleCollectService;
	private final TitleFillService titleFillService;
	private final SingleTitleFetcher singleTitleFetcher;
	private final Clock clock;

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		// 1. 꺼져 있으면 아무것도 하지 않고 정상 종료한다
		if (!properties.enabled()) {
			log.info("인기 작품 상세 채우기가 꺼져 있어 건너뛴다(app.popularity.fill.enabled=false)");
			return RepeatStatus.FINISHED;
		}

		// 2. 순위 16개의 후보를 받는다(캐시를 거치지 않고 방금 쌓은 스냅샷으로 계산)
		List<List<PopularFillTarget>> rankings = popularityRankingService.findFillCandidates(properties.topN());

		// 3. 모든 후보의 키(중복 제거)를 모아 이미 title 표에 있는 키를 구한다
		Set<TitleKey> candidateKeys = new HashSet<>();
		for (List<PopularFillTarget> ranking : rankings) {
			for (PopularFillTarget candidate : ranking) {
				candidateKeys.add(new TitleKey(candidate.mediaType(), candidate.tmdbId()));
			}
		}
		Set<TitleKey> existingKeys = new HashSet<>();
		if (!candidateKeys.isEmpty()) {
			existingKeys = titleCollectService.findExistingKeys(candidateKeys);
		}

		// 4. 채울 대상을 고른다. 상한으로 잘려 나간 수 = 후보 중 대상도 아니고 이미 있지도 않은 작품
		List<PopularFillTarget> targets = PopularityFillTargets.pick(rankings, existingKeys, properties.maxTitles());
		int alreadyExists = existingKeys.size();
		int cutOff = candidateKeys.size() - alreadyExists - targets.size();
		if (targets.isEmpty()) {
			log.info("채울 인기 작품이 없다: 후보={}, 이미 있음={}", candidateKeys.size(), alreadyExists);
			return RepeatStatus.FINISHED;
		}

		// 5. 대상마다 TMDB에서 받고(트랜잭션 밖) 한 트랜잭션으로 저장한다
		int filled = 0;
		int notOnTmdb = 0;
		int failed = 0;
		for (PopularFillTarget target : targets) {
			try {
				FetchedTitle fetched = singleTitleFetcher.fetch(target.mediaType(), target.tmdbId());
				// 6. TMDB에 없으면(404) 이 작품만 건너뛴다
				if (fetched == null) {
					notOnTmdb++;
					continue;
				}
				// 7. 저장한다. 그 사이 수집이 먼저 넣었으면 false라 채운 수에 세지 않는다
				boolean saved = titleFillService.saveFetched(fetched, target.fallbackTitle(), Instant.now(clock));
				if (saved) {
					filled++;
				}
			} catch (TmdbException | DataIntegrityViolationException e) {
				// 8. 예상 가능한 실패(TMDB 오류·동시 삽입)는 이 작품만 건너뛴다. 예외 메시지에 접속 정보가 섞일 수 있어 원인 클래스 이름만 남긴다
				failed++;
				log.warn("인기 작품 상세를 채우지 못해 건너뛴다: mediaType={}, tmdbId={}, error={}",
						target.mediaType(), target.tmdbId(), ExceptionRootCause.rootCauseName(e));
			}
		}

		// 9. 결과를 남기고 채운 수를 Step의 쓴 건수로 기록한다
		contribution.incrementWriteCount(filled);
		log.info("인기 작품 상세 채우기 종료: 후보={}, 이미 있음={}, 채움={}, TMDB에 없음={}, 실패={}, 상한으로 제외={}",
				candidateKeys.size(), alreadyExists, filled, notOnTmdb, failed, cutOff);
		return RepeatStatus.FINISHED;
	}
}

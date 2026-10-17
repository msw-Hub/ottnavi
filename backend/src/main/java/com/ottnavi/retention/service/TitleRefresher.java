package com.ottnavi.retention.service;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.rule.RuntimeDefaults;
import com.ottnavi.catalog.service.TitleDetailCollectService;
import com.ottnavi.catalog.service.model.StaleTitle;
import com.ottnavi.catalog.service.model.TitleDetail;
import com.ottnavi.collect.step.detail.DetailTarget;
import com.ottnavi.collect.step.detail.MovieDetailProcessor;
import com.ottnavi.collect.step.detail.TvDetailProcessor;
import com.ottnavi.infra.tmdb.TmdbClient;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 오래된 작품의 상세를 TMDB에서 다시 받아 저장한다(Task 083). 수집 상세 Step의 Processor와 저장 서비스를 그대로 재사용해
 * 정리 쪽과 수집 쪽의 저장 결과가 달라지지 않게 한다. TMDB 호출은 트랜잭션 밖에서 하고 저장만 서비스의 트랜잭션에 맡긴다.
 */
@Component
public class TitleRefresher {

	private final MovieDetailProcessor movieProcessor;
	private final TvDetailProcessor tvProcessor;
	private final TitleDetailCollectService titleDetailCollectService;
	private final Clock clock;

	public TitleRefresher(TmdbClient tmdbClient, RuntimeDefaults runtimeDefaults, MeterRegistry meterRegistry,
			TitleDetailCollectService titleDetailCollectService, Clock clock) {
		this.movieProcessor = new MovieDetailProcessor(tmdbClient, runtimeDefaults);
		this.tvProcessor = new TvDetailProcessor(tmdbClient, runtimeDefaults, meterRegistry);
		this.titleDetailCollectService = titleDetailCollectService;
		this.clock = clock;
	}

	/**
	 * 상세를 다시 받아 저장하고 저장했으면 true를 돌려준다.
	 * Processor가 404(드라마는 시즌 404 포함)를 만나면 null을 돌려주므로 false가 되고, 그 작품은 그대로 둔다.
	 * 한도 초과 같은 TMDB 실패는 TmdbException으로 올라가니 호출하는 쪽이 처리한다.
	 */
	public boolean refresh(StaleTitle title) {
		// 1. 종류에 맞는 Processor로 상세를 받는다
		DetailTarget target = new DetailTarget(title.titleId(), title.mediaType(), title.tmdbId());
		TitleDetail detail;
		if (title.mediaType() == MediaType.MOVIE) {
			detail = movieProcessor.process(target);
		} else {
			detail = tvProcessor.process(target);
		}
		if (detail == null) {
			return false;
		}

		// 2. 받은 상세를 저장한다(마지막 수집 시각이 지금으로 갱신된다)
		titleDetailCollectService.saveDetails(List.of(detail), clock.instant());
		return true;
	}
}

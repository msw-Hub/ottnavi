package com.ottnavi.retention.service;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.service.model.StaleTitle;
import com.ottnavi.infra.tmdb.TmdbClient;
import com.ottnavi.infra.tmdb.exception.TmdbException;
import com.ottnavi.infra.tmdb.exception.TmdbNotFoundException;
import com.ottnavi.retention.service.model.TitleExistence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TMDB에 작품이 아직 있는지 작품 단위 상세 조회로 확인한다(Task 083). 외부 호출이므로 트랜잭션 밖에서 부른다.
 * 삭제 근거는 작품 단위 조회의 404 하나뿐이다. 드라마 시즌 조회의 404는 시즌만 없는 경우일 수 있어 여기서 보지 않는다.
 * 429·5xx·연결 실패는 "알 수 없음"이라 삭제하지 않는다(일시 장애로 데이터를 잃는 사고를 막는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TitleExistenceProbe {

	private static final String LANGUAGE_KO = "ko-KR";

	private final TmdbClient tmdbClient;

	/** 작품이 TMDB에 있으면 EXISTS, 작품 단위 404면 GONE, 그 밖의 실패·빈 응답이면 UNKNOWN */
	public TitleExistence probe(StaleTitle title) {
		try {
			// 1. 작품 단위 상세를 한 번 부른다(추가 정보 없이 가볍게)
			Object response;
			if (title.mediaType() == MediaType.MOVIE) {
				response = tmdbClient.getMovie(title.tmdbId(), LANGUAGE_KO, null);
			} else {
				response = tmdbClient.getTv(title.tmdbId(), LANGUAGE_KO, null);
			}
			// 2. 본문이 비어 왔으면 있는지 판단할 수 없다
			if (response == null) {
				log.warn("TMDB 응답이 비어 있어 작품 존재 여부를 알 수 없다: titleId={}, tmdbId={}", title.titleId(), title.tmdbId());
				return TitleExistence.UNKNOWN;
			}
			return TitleExistence.EXISTS;
		} catch (TmdbNotFoundException e) {
			// 3. 작품 단위 404는 TMDB에서 사라진 것이다
			log.warn("TMDB에서 사라진 작품이다(404): titleId={}, mediaType={}, tmdbId={}", title.titleId(), title.mediaType(),
					title.tmdbId());
			return TitleExistence.GONE;
		} catch (TmdbException e) {
			// 4. 한도 초과·서버 오류·연결 실패 등은 이번에는 판단하지 않는다
			log.warn("TMDB 확인에 실패해 이번에는 건드리지 않는다: titleId={}, tmdbId={}, 사유={}", title.titleId(), title.tmdbId(),
					e.getMessage());
			return TitleExistence.UNKNOWN;
		}
	}
}

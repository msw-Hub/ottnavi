package com.ottnavi.admin;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.ottnavi.catalog.domain.entity.AvailabilityChange;
import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.entity.WatchUnit;
import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.repository.AvailabilityChangeRepository;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.repository.WatchUnitRepository;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.domain.repository.OttServiceRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Task 045 단계 5: 실행 이력 조회와 실행별 변경 조회를 시험한다(Docker 필요).
 * 변경 목록은 availability_change를 직접 넣어 확인하고, 처리 건수는 가짜 TMDB로 실제 수집을 돌려 확인한다.
 */
class AdminCollectHistoryApiTest extends AdminCollectApiTestSupport {

	private static final String DAY = "2026-03-10";
	private static final Instant NOW = Instant.parse("2026-03-10T00:00:00Z");

	@Autowired
	private TitleRepository titleRepository;

	@Autowired
	private WatchUnitRepository watchUnitRepository;

	@Autowired
	private AvailabilityChangeRepository availabilityChangeRepository;

	@Autowired
	private OttServiceRepository ottServiceRepository;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	// 이력 목록

	@Test
	void 이력은_대상_날짜_내림차순_같으면_실행_ID_내림차순이다() throws Exception {
		// given: 03-10 첫 실행은 실패, 같은 날짜 재시작은 성공, 그 뒤 03-09 실행
		stubFailingCollection();
		long failedId = runToEnd(DAY);
		wireMock.resetAll();
		stubSuccessfulCollection(0);
		long restartedId = executionIdOf(postAction(failedId, "restart").andExpect(status().isAccepted()));
		awaitFinished(restartedId);
		long olderDayId = runToEnd("2026-03-09");

		// when
		ResultActions result = getExecutions(0, 20);

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.content[0].executionId").value(restartedId))
				.andExpect(jsonPath("$.data.content[0].targetDate").value(DAY))
				.andExpect(jsonPath("$.data.content[1].executionId").value(failedId))
				.andExpect(jsonPath("$.data.content[2].executionId").value(olderDayId))
				.andExpect(jsonPath("$.data.content[2].targetDate").value("2026-03-09"));
	}

	@Test
	void page와_size로_나누고_totalPages를_계산한다() throws Exception {
		// given: 서로 다른 날짜 3건
		runToEnd("2026-03-08");
		runToEnd("2026-03-09");
		long newestId = runToEnd(DAY);

		// when, then: 크기 2 → 2쪽, 첫 쪽은 2건
		getExecutions(0, 2)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(2))
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.content.length()").value(2))
				.andExpect(jsonPath("$.data.content[0].executionId").value(newestId));
		getExecutions(1, 2)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(1))
				.andExpect(jsonPath("$.data.content.length()").value(1))
				.andExpect(jsonPath("$.data.content[0].targetDate").value("2026-03-08"));
	}

	@Test
	void size가_101이면_400_VALIDATION_FAILED_size다() throws Exception {
		// given, when, then
		getExecutions(0, 101)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
	}

	@Test
	void size가_0이면_400이다() throws Exception {
		// given, when, then
		getExecutions(0, 0)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
	}

	@Test
	void page가_음수면_400이다() throws Exception {
		// given, when, then
		getExecutions(-1, 20)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("page"));
	}

	@Test
	void 실행_이력이_없으면_빈_content와_totalElements_0이다() throws Exception {
		// given: 초기화로 Batch 기록이 비어 있다

		// when, then
		getExecutions(0, 20)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(0))
				.andExpect(jsonPath("$.data.totalElements").value(0))
				.andExpect(jsonPath("$.data.totalPages").value(0));
	}

	@Test
	void processedCount는_영화와_드라마_상세_Step의_쓰기_건수_합이다() throws Exception {
		// given: 영화 1편과 드라마 1편(방영된 시즌 1개)
		stubOneMovieAndOneTv();

		// when
		long executionId = runToEnd(DAY);

		// then
		getExecutions(0, 20)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].executionId").value(executionId))
				.andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"))
				.andExpect(jsonPath("$.data.content[0].processedCount").value(2));
	}

	@Test
	void changedCount는_그_실행의_availability_change_건수다() throws Exception {
		// given: 실행 두 건, 첫 실행에 변경 3건·둘째 실행에 변경 1건
		long first = runToEnd("2026-03-09");
		long second = runToEnd(DAY);
		OttService service = anyServices().get(0);
		Title title = titleRepository.save(Title.create(MediaType.MOVIE, 700001, "가", NOW));
		WatchUnit unit = watchUnitRepository.save(WatchUnit.createMovie(title, 100, NOW));
		saveChange(unit, service, null, AvailabilityStatus.AVAILABLE, first);
		saveChange(unit, anyServices().get(1), null, AvailabilityStatus.AVAILABLE, first);
		saveChange(unit, anyServices().get(2), null, AvailabilityStatus.UNKNOWN, first);
		saveChange(unit, service, AvailabilityStatus.AVAILABLE, AvailabilityStatus.NOT_AVAILABLE, second);

		// when, then
		getExecutions(0, 20)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].executionId").value(second))
				.andExpect(jsonPath("$.data.content[0].changedCount").value(1))
				.andExpect(jsonPath("$.data.content[1].executionId").value(first))
				.andExpect(jsonPath("$.data.content[1].changedCount").value(3));
	}

	@Test
	void 실패한_실행의_failureReason은_짧은_예외명으로_시작한다() throws Exception {
		// given
		stubFailingCollection();
		long executionId = runToEnd(DAY);

		// when, then: 패키지 경로(org., com.)가 아니라 클래스 이름으로 시작한다
		getExecutions(0, 20)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].executionId").value(executionId))
				.andExpect(jsonPath("$.data.content[0].status").value("FAILED"))
				.andExpect(jsonPath("$.data.content[0].failureReason").value(
						org.hamcrest.Matchers.matchesPattern("^[A-Z][A-Za-z0-9]*(Exception|Error)\\b.*")));
	}

	@Test
	void 시각은_KST_오프셋으로_나간다() throws Exception {
		// given
		runToEnd(DAY);

		// when, then
		getExecutions(0, 20)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].startedAt").value(
						org.hamcrest.Matchers.endsWith("+09:00")))
				.andExpect(jsonPath("$.data.content[0].endedAt").value(
						org.hamcrest.Matchers.endsWith("+09:00")));
	}

	@Test
	void 토큰_없이_이력을_조회하면_401이다() throws Exception {
		// given, when, then
		mockMvc.perform(get(EXECUTIONS_PATH))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
		mockMvc.perform(get(EXECUTIONS_PATH + "/1/changes"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	@Test
	void 한_쪽에_여러_실행이_있어도_쿼리_수가_행_수에_비례하지_않는다() throws Exception {
		// given: 실행 3건과 변경 3건(JPA로 읽는 부분은 변경 건수 집계 한 번뿐이어야 한다)
		long a = runToEnd("2026-03-08");
		long b = runToEnd("2026-03-09");
		long c = runToEnd(DAY);
		Title title = titleRepository.save(Title.create(MediaType.MOVIE, 700002, "가", NOW));
		WatchUnit unit = watchUnitRepository.save(WatchUnit.createMovie(title, 100, NOW));
		OttService service = anyServices().get(0);
		saveChange(unit, service, null, AvailabilityStatus.AVAILABLE, a);
		saveChange(unit, service, null, AvailabilityStatus.AVAILABLE, b);
		saveChange(unit, service, null, AvailabilityStatus.AVAILABLE, c);
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();

		// when
		getExecutions(0, 20).andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(3));

		// then: 실행 3건인데 JPA 쿼리는 변경 건수 집계 1번이다
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
		statistics.setStatisticsEnabled(false);
	}

	// 변경 목록

	@Test
	void 변경_목록은_제목_시즌_서비스_순이고_영화_시즌은_null이다() throws Exception {
		// given: 제목 "가"(드라마 시즌 2·1), 제목 "나"(영화)
		long executionId = runToEnd(DAY);
		List<OttService> services = anyServices();
		Title tvTitle = titleRepository.save(Title.create(MediaType.TV, 800001, "가", NOW));
		WatchUnit season1 = watchUnitRepository.save(WatchUnit.createSeason(tvTitle, 1, 5001, "시즌 1", 10, 8, 480, NOW));
		WatchUnit season2 = watchUnitRepository.save(WatchUnit.createSeason(tvTitle, 2, 5002, "시즌 2", 10, 8, 480, NOW));
		Title movieTitle = titleRepository.save(Title.create(MediaType.MOVIE, 800002, "나", NOW));
		WatchUnit movie = watchUnitRepository.save(WatchUnit.createMovie(movieTitle, 100, NOW));
		saveChange(movie, services.get(0), null, AvailabilityStatus.AVAILABLE, executionId);
		saveChange(season2, services.get(0), null, AvailabilityStatus.AVAILABLE, executionId);
		saveChange(season1, services.get(1), null, AvailabilityStatus.AVAILABLE, executionId);
		saveChange(season1, services.get(0), null, AvailabilityStatus.AVAILABLE, executionId);

		// when, then
		getChanges(executionId, 0, 20)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(4))
				.andExpect(jsonPath("$.data.content[0].title").value("가"))
				.andExpect(jsonPath("$.data.content[0].seasonNumber").value(1))
				.andExpect(jsonPath("$.data.content[0].ottServiceId").value(services.get(0).getId()))
				.andExpect(jsonPath("$.data.content[1].seasonNumber").value(1))
				.andExpect(jsonPath("$.data.content[1].ottServiceId").value(services.get(1).getId()))
				.andExpect(jsonPath("$.data.content[2].seasonNumber").value(2))
				.andExpect(jsonPath("$.data.content[3].title").value("나"))
				.andExpect(jsonPath("$.data.content[3].seasonNumber").isEmpty())
				.andExpect(jsonPath("$.data.content[3].tmdbId").value(800002));
	}

	@Test
	void 처음_확인된_작품의_previousStatus는_null이다() throws Exception {
		// given
		long executionId = runToEnd(DAY);
		Title title = titleRepository.save(Title.create(MediaType.MOVIE, 800003, "가", NOW));
		WatchUnit unit = watchUnitRepository.save(WatchUnit.createMovie(title, 100, NOW));
		List<OttService> services = anyServices();
		saveChange(unit, services.get(0), null, AvailabilityStatus.AVAILABLE, executionId);
		saveChange(unit, services.get(1), AvailabilityStatus.AVAILABLE, AvailabilityStatus.UNKNOWN, executionId);

		// when, then
		getChanges(executionId, 0, 20)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].previousStatus").isEmpty())
				.andExpect(jsonPath("$.data.content[0].currentStatus").value("AVAILABLE"))
				.andExpect(jsonPath("$.data.content[1].previousStatus").value("AVAILABLE"))
				.andExpect(jsonPath("$.data.content[1].currentStatus").value("UNKNOWN"));
	}

	@Test
	void mediaType은_소문자_movie_tv다() throws Exception {
		// given
		long executionId = runToEnd(DAY);
		Title movieTitle = titleRepository.save(Title.create(MediaType.MOVIE, 800004, "가", NOW));
		WatchUnit movie = watchUnitRepository.save(WatchUnit.createMovie(movieTitle, 100, NOW));
		Title tvTitle = titleRepository.save(Title.create(MediaType.TV, 800005, "나", NOW));
		WatchUnit season = watchUnitRepository.save(WatchUnit.createSeason(tvTitle, 1, 5003, "시즌 1", 10, 8, 480, NOW));
		OttService service = anyServices().get(0);
		saveChange(movie, service, null, AvailabilityStatus.AVAILABLE, executionId);
		saveChange(season, service, null, AvailabilityStatus.AVAILABLE, executionId);

		// when, then
		getChanges(executionId, 0, 20)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].mediaType").value("movie"))
				.andExpect(jsonPath("$.data.content[1].mediaType").value("tv"));
	}

	@Test
	void 없는_실행의_변경_목록은_404다() throws Exception {
		// given, when, then
		getChanges(999999L, 0, 20)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void 다른_실행의_변경은_섞이지_않는다() throws Exception {
		// given
		long first = runToEnd("2026-03-09");
		long second = runToEnd(DAY);
		Title title = titleRepository.save(Title.create(MediaType.MOVIE, 800006, "가", NOW));
		WatchUnit unit = watchUnitRepository.save(WatchUnit.createMovie(title, 100, NOW));
		List<OttService> services = anyServices();
		saveChange(unit, services.get(0), null, AvailabilityStatus.AVAILABLE, first);
		saveChange(unit, services.get(1), null, AvailabilityStatus.AVAILABLE, second);

		// when, then
		getChanges(first, 0, 20)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].ottServiceId").value(services.get(0).getId()));
	}

	@Test
	void 변경_목록도_page_size로_나뉜다() throws Exception {
		// given: 변경 3건
		long executionId = runToEnd(DAY);
		Title title = titleRepository.save(Title.create(MediaType.MOVIE, 800007, "가", NOW));
		WatchUnit unit = watchUnitRepository.save(WatchUnit.createMovie(title, 100, NOW));
		List<OttService> services = anyServices();
		saveChange(unit, services.get(0), null, AvailabilityStatus.AVAILABLE, executionId);
		saveChange(unit, services.get(1), null, AvailabilityStatus.AVAILABLE, executionId);
		saveChange(unit, services.get(2), null, AvailabilityStatus.AVAILABLE, executionId);

		// when, then
		getChanges(executionId, 1, 2)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(1))
				.andExpect(jsonPath("$.data.size").value(2))
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.content.length()").value(1))
				.andExpect(jsonPath("$.data.content[0].ottServiceId").value(services.get(2).getId()));
	}

	@Test
	void 변경_목록의_쿼리_수는_행_수와_무관하다() throws Exception {
		// given: 서로 다른 작품 5건의 변경
		long executionId = runToEnd(DAY);
		OttService service = anyServices().get(0);
		for (int i = 0; i < 5; i++) {
			Title title = titleRepository.save(Title.create(MediaType.MOVIE, 810000 + i, "작품" + i, NOW));
			WatchUnit unit = watchUnitRepository.save(WatchUnit.createMovie(title, 100, NOW));
			saveChange(unit, service, null, AvailabilityStatus.AVAILABLE, executionId);
		}
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();

		// when
		getChanges(executionId, 0, 20).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(5));

		// then: 쪽이 다 차지 않아 목록 쿼리 1번뿐이다(작품·시청 단위·서비스는 join fetch)
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
		statistics.setStatisticsEnabled(false);
	}

	// 보조

	private ResultActions getExecutions(int page, int size) throws Exception {
		return mockMvc.perform(get(EXECUTIONS_PATH)
				.param("page", String.valueOf(page))
				.param("size", String.valueOf(size))
				.header("Authorization", "Bearer " + batchToken));
	}

	private ResultActions getChanges(long executionId, int page, int size) throws Exception {
		return mockMvc.perform(get(EXECUTIONS_PATH + "/" + executionId + "/changes")
				.param("page", String.valueOf(page))
				.param("size", String.valueOf(size))
				.header("Authorization", "Bearer " + batchToken));
	}

	/** 수집을 요청해 끝날 때까지 기다리고 실행 ID를 돌려준다(성공·실패는 호출 전에 등록한 TMDB 응답이 정한다) */
	private long runToEnd(String targetDate) throws Exception {
		long executionId = executionIdOf(postCreate(createBody(targetDate, "ALL")).andExpect(status().isAccepted()));
		awaitFinished(executionId);
		return executionId;
	}

	/** 서비스 시드를 ID 순으로 돌려준다 */
	private List<OttService> anyServices() {
		List<OttService> services = ottServiceRepository.findAll();
		services.sort(new java.util.Comparator<OttService>() {
			@Override
			public int compare(OttService left, OttService right) {
				return Long.compare(left.getId(), right.getId());
			}
		});
		return services;
	}

	private void saveChange(WatchUnit unit, OttService service, AvailabilityStatus before, AvailabilityStatus after,
			long executionId) {
		availabilityChangeRepository.save(AvailabilityChange.create(unit, service, before, after, executionId, NOW));
	}

	/** 영화 한 편(900001)과 방영된 시즌 1개짜리 드라마(800001)가 끝까지 수집되게 TMDB 응답을 등록한다 */
	private static void stubOneMovieAndOneTv() {
		wireMock.stubFor(WireMock.get(urlPathEqualTo("/3/discover/movie"))
				.withQueryParam("with_watch_providers", equalTo("8|1796"))
				.withQueryParam("page", equalTo("1"))
				.willReturn(json("{\"page\":1,\"results\":[{\"id\":900001,\"title\":\"후보 900001\"}],"
						+ "\"total_pages\":1,\"total_results\":1}")));
		wireMock.stubFor(WireMock.get(urlPathEqualTo("/3/discover/tv"))
				.withQueryParam("with_watch_providers", equalTo("8|1796"))
				.withQueryParam("page", equalTo("1"))
				.willReturn(json("{\"page\":1,\"results\":[{\"id\":800001,\"name\":\"후보 800001\"}],"
						+ "\"total_pages\":1,\"total_results\":1}")));
		wireMock.stubFor(WireMock.get(urlPathEqualTo("/3/movie/900001")).withQueryParam("language", equalTo("ko-KR"))
				.willReturn(json("{\"id\":900001,\"title\":\"후보 900001\",\"original_title\":\"Original\","
						+ "\"overview\":\"줄거리\",\"release_date\":\"2024-05-01\",\"runtime\":100,\"genres\":[],"
						+ "\"original_language\":\"ko\",\"popularity\":1.5,\"vote_average\":7.0,"
						+ "\"credits\":{\"cast\":[]}}")));
		wireMock.stubFor(WireMock.get(urlPathEqualTo("/3/movie/900001/watch/providers"))
				.willReturn(json("{\"id\":1,\"results\":{\"KR\":{\"flatrate\":[{\"provider_id\":8}]}}}")));
		wireMock.stubFor(WireMock.get(urlPathEqualTo("/3/tv/800001")).withQueryParam("language", equalTo("ko-KR"))
				.willReturn(json("{\"id\":800001,\"name\":\"후보 800001\",\"original_name\":\"Original\","
						+ "\"overview\":\"줄거리\",\"first_air_date\":\"2023-03-01\",\"status\":\"Ended\","
						+ "\"genres\":[],\"seasons\":[{\"id\":810001,\"season_number\":1,\"name\":\"시즌 1\","
						+ "\"air_date\":\"2023-03-01\",\"episode_count\":2}],\"credits\":{\"cast\":[]}}")));
		wireMock.stubFor(WireMock.get(urlPathEqualTo("/3/tv/800001/season/1")).withQueryParam("language", equalTo("ko-KR"))
				.willReturn(json("{\"id\":810001,\"season_number\":1,\"name\":\"시즌 1\",\"air_date\":\"2023-03-01\","
						+ "\"episodes\":[{\"episode_number\":1,\"air_date\":\"2023-03-01\",\"runtime\":60},"
						+ "{\"episode_number\":2,\"air_date\":\"2023-03-02\",\"runtime\":60}]}")));
		wireMock.stubFor(WireMock.get(urlPathEqualTo("/3/tv/800001/season/1/watch/providers"))
				.willReturn(json("{\"id\":1,\"results\":{\"KR\":{\"flatrate\":[{\"provider_id\":337}]}}}")));
	}
}

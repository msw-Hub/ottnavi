package com.ottnavi.llmdraft.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.infra.llm.GeminiClient;
import com.ottnavi.infra.llm.exception.LlmCallFailedException;
import com.ottnavi.infra.llm.exception.LlmUpstreamBusyException;
import com.ottnavi.llmdraft.service.ConflictingJobChecker;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.support.TestTokens;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 영어 줄거리 번역 관리자 API 통합 시험(Docker 필요). Gemini 클라이언트는 목이라 실제 호출은 없다.
 * 초안 생성 → 목록 → 반영 → DB·상세 캐시 확인의 핵심 흐름과 반영 거절 조건, 되돌리기, 일괄, 후보 정렬을 확인한다.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.llm.bulk.call-interval=0s")   // 일괄 시험이 호출 사이를 기다리지 않게 한다
class AdminLlmApiTest extends IntegrationTestSupport {

    private static final String BASE = "/api/admin/llm";
    private static final String ORIGINAL = "A detective hunts a killer in a quiet seaside town.";
    private static final String TRANSLATED = "한 형사가 조용한 해안 마을에서 살인범을 쫓는다.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private GeminiClient geminiClient;

    @MockitoBean
    private ConflictingJobChecker conflictingJobChecker;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final List<Long> titleIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void 준비한다() {
        clearRedis();
        when(geminiClient.isEnabled()).thenReturn(true);
        when(geminiClient.translateToKorean(anyString())).thenReturn(TRANSLATED);
        when(conflictingJobChecker.isAnyRunning()).thenReturn(false);
    }

    @AfterEach
    void 정리한다() {
        for (Long userId : userIds) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);   // 찜은 FK CASCADE로 함께 지워진다
        }
        for (Long titleId : titleIds) {
            jdbcTemplate.update("DELETE FROM watch_unit WHERE title_id = ?", titleId);
            jdbcTemplate.update("DELETE FROM title WHERE id = ?", titleId);
        }
        clearRedis();
    }

    private void clearRedis() {
        Set<String> keys = redis.keys("llm:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private String adminBearer() {
        return new TestTokens(jwtTokenProvider).adminBearer();
    }

    private long insertTitle(int tmdbId, double popularity, String overviewLang, String overview) {
        long id = jdbcTemplate.queryForObject("INSERT INTO title (media_type, tmdb_id, title, overview, overview_lang, "
                + "popularity, fetched_at) VALUES ('MOVIE', ?, ?, ?, ?, ?, now()) RETURNING id", Long.class,
                tmdbId, "번역시험 " + tmdbId, overview, overviewLang, popularity);
        titleIds.add(id);
        return id;
    }

    /** 관리자가 번역을 반영한 작품을 넣는다. translatedDaysAgo일 전에 반영한 것으로 번역 시각을 적는다 */
    private long insertTranslatedTitle(int tmdbId, int translatedDaysAgo) {
        long id = jdbcTemplate.queryForObject("INSERT INTO title (media_type, tmdb_id, title, overview, overview_lang, "
                + "overview_translated_at, popularity, fetched_at) VALUES ('MOVIE', ?, ?, ?, 'ko', "
                + "now() - make_interval(days => ?), 1.0, now()) RETURNING id", Long.class,
                tmdbId, "번역시험 " + tmdbId, TRANSLATED, translatedDaysAgo);
        titleIds.add(id);
        return id;
    }

    private boolean isOverviewCleared(long titleId) {
        return jdbcTemplate.queryForObject("SELECT overview IS NULL AND overview_lang IS NULL "
                + "AND overview_translated_at IS NULL FROM title WHERE id = ?", Boolean.class, titleId);
    }

    private void insertWishlist(long titleId, int userCount) {
        long unitId = jdbcTemplate.queryForObject("INSERT INTO watch_unit (title_id, unit_type, total_runtime_min, "
                + "fetched_at) VALUES (?, 'MOVIE', 100, now()) RETURNING id", Long.class, titleId);
        for (int i = 0; i < userCount; i++) {
            long userId = jdbcTemplate.queryForObject("INSERT INTO users (email, name, oauth_provider, oauth_subject) "
                    + "VALUES (?, '번역시험', 'GOOGLE', ?) RETURNING id", Long.class,
                    "llm-" + titleId + "-" + i + "@test.com", "sub-llm-" + titleId + "-" + i);
            userIds.add(userId);
            jdbcTemplate.update("INSERT INTO wishlist_item (user_id, watch_unit_id, priority) VALUES (?, ?, 'MUST')",
                    userId, unitId);
        }
    }

    /** 일괄 번역이 끝날 때까지(최대 약 10초) 기다린다 */
    private void waitBulkFinished() throws Exception {
        String state = "";
        for (int i = 0; i < 100 && !"COMPLETED".equals(state); i++) {
            Thread.sleep(100);
            MvcResult result = mockMvc.perform(get(BASE + "/drafts").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                    .andReturn();
            state = json(result).path("data").path("bulkRun").path("state").asText();
        }
        assertThat(state).isEqualTo("COMPLETED");
    }

    private JsonNode json(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createDraft(long titleId) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/drafts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titleId\":" + titleId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).path("data").path("draftId").asText();
    }

    @Test
    void 후보는_찜_많은_순_다음_인기순이고_영어가_아닌_작품은_빠지며_요약이_들어_있다() throws Exception {
        // given: 찜 2명(인기 낮음), 찜 1명(인기 높음), 찜 없음(인기 중간), 한국어(제외)
        long twoWishes = insertTitle(880001, 1.0, "en", ORIGINAL);
        long oneWish = insertTitle(880002, 9.0, "en", ORIGINAL);
        long noWish = insertTitle(880003, 5.0, "en", ORIGINAL);
        long korean = insertTitle(880004, 100.0, "ko", TRANSLATED);
        insertWishlist(twoWishes, 2);
        insertWishlist(oneWish, 1);

        // when
        MvcResult result = mockMvc.perform(get(BASE + "/overview-candidates").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.dailyLimit").value(400))
                .andExpect(jsonPath("$.data.summary.perMinuteLimit").value(12))
                .andExpect(jsonPath("$.data.summary.isEnabled").value(true))
                .andExpect(jsonPath("$.data.summary.model").isNotEmpty())
                .andReturn();

        // then: 내가 넣은 작품들의 상대 순서와 제외 확인
        List<Long> order = new ArrayList<>();
        JsonNode content = json(result).path("data").path("content");
        for (JsonNode item : content) {
            order.add(item.path("titleId").asLong());
            if (item.path("titleId").asLong() == twoWishes) {
                assertThat(item.path("wishlistCount").asLong()).isEqualTo(2);
                assertThat(item.path("overview").asText()).isEqualTo(ORIGINAL);
                assertThat(item.path("hasDraft").asBoolean()).isFalse();
            }
        }
        assertThat(order).contains(twoWishes, oneWish, noWish).doesNotContain(korean);
        assertThat(order.indexOf(twoWishes)).isLessThan(order.indexOf(oneWish));
        assertThat(order.indexOf(oneWish)).isLessThan(order.indexOf(noWish));
    }

    @Test
    void 키가_없어도_후보_조회는_동작하고_isEnabled가_false이다() throws Exception {
        // given
        when(geminiClient.isEnabled()).thenReturn(false);

        // when, then
        mockMvc.perform(get(BASE + "/overview-candidates").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.isEnabled").value(false));
    }

    @Test
    void 초안을_만들고_반영하면_DB와_상세_캐시가_한국어로_바뀐다() throws Exception {
        // given: 영어 줄거리 작품의 상세를 한 번 조회해 상세 캐시에 영어를 채워 둔다
        long titleId = insertTitle(880011, 1.0, "en", ORIGINAL);
        mockMvc.perform(get("/api/public/titles/movie/880011"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overviewLang").value("en"));

        // when: 초안 생성
        String draftId = createDraft(titleId);

        // then: 목록에 통과 초안으로 보인다
        mockMvc.perform(get(BASE + "/drafts").param("status", "PASSED").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].draftId").value(draftId))
                .andExpect(jsonPath("$.data.content[0].original").value(ORIGINAL))
                .andExpect(jsonPath("$.data.content[0].translated").value(TRANSLATED))
                .andExpect(jsonPath("$.data.content[0].isGuardPassed").value(true))
                .andExpect(jsonPath("$.data.bulkRun").value(nullValue()));

        // when: 반영
        mockMvc.perform(post(BASE + "/drafts/apply").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"draftIds\":[\"" + draftId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedCount").value(1))
                .andExpect(jsonPath("$.data.results[0].outcome").value("APPLIED"));

        // then: DB가 바뀌고, 캐시가 지워져 상세가 새 줄거리를 보여 주며, 반영한 초안은 목록에서 사라진다
        assertThat(jdbcTemplate.queryForObject("SELECT overview FROM title WHERE id = ?", String.class, titleId))
                .isEqualTo(TRANSLATED);
        assertThat(jdbcTemplate.queryForObject("SELECT overview_lang FROM title WHERE id = ?", String.class, titleId))
                .isEqualTo("ko");
        assertThat(jdbcTemplate.queryForObject("SELECT overview_translated_at IS NOT NULL FROM title WHERE id = ?",
                Boolean.class, titleId)).isTrue();   // 반영하면 번역 반영 시각이 적힌다
        mockMvc.perform(get("/api/public/titles/movie/880011"))
                .andExpect(jsonPath("$.data.overview").value(TRANSLATED))
                .andExpect(jsonPath("$.data.overviewLang").value("ko"));
        mockMvc.perform(get(BASE + "/drafts").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    void 번역한_뒤_원문이_바뀌었으면_건너뛰고_DB를_바꾸지_않는다() throws Exception {
        // given
        long titleId = insertTitle(880012, 1.0, "en", ORIGINAL);
        String draftId = createDraft(titleId);
        jdbcTemplate.update("UPDATE title SET overview = 'A newer English overview.' WHERE id = ?", titleId);

        // when, then
        mockMvc.perform(post(BASE + "/drafts/apply").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"draftIds\":[\"" + draftId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedCount").value(0))
                .andExpect(jsonPath("$.data.results[0].outcome").value("SKIPPED"))
                .andExpect(jsonPath("$.data.results[0].reason").value("ORIGINAL_CHANGED"));
        assertThat(jdbcTemplate.queryForObject("SELECT overview_lang FROM title WHERE id = ?", String.class, titleId))
                .isEqualTo("en");
    }

    @Test
    void 검사에서_걸린_초안은_반영을_거절하고_allPassed에서도_빠진다() throws Exception {
        // given: 영어가 그대로 돌아온 초안
        when(geminiClient.translateToKorean(anyString())).thenReturn("A detective hunts a killer in a quiet town.");
        long titleId = insertTitle(880013, 1.0, "en", ORIGINAL);
        String draftId = createDraft(titleId);

        // when, then: 지정해서 반영하면 GUARD_FAILED로 건너뛴다
        mockMvc.perform(post(BASE + "/drafts/apply").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"draftIds\":[\"" + draftId + "\"]}"))
                .andExpect(jsonPath("$.data.results[0].reason").value("GUARD_FAILED"));
        // allPassed는 통과 초안만 대상이라 결과가 비어 있다
        mockMvc.perform(post(BASE + "/drafts/apply").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allPassed\":true}"))
                .andExpect(jsonPath("$.data.results", hasSize(0)));
        assertThat(jdbcTemplate.queryForObject("SELECT overview_lang FROM title WHERE id = ?", String.class, titleId))
                .isEqualTo("en");
    }

    @Test
    void 사라진_초안을_반영하면_DRAFT_NOT_FOUND로_건너뛴다() throws Exception {
        mockMvc.perform(post(BASE + "/drafts/apply").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"draftIds\":[\"gone\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].reason").value("DRAFT_NOT_FOUND"))
                .andExpect(jsonPath("$.data.results[0].titleId").value(nullValue()));
    }

    @Test
    void 수집이_실행_중이면_반영과_되돌리기는_409_LLM_APPLY_CONFLICT이다() throws Exception {
        // given
        long titleId = insertTitle(880014, 1.0, "en", ORIGINAL);
        String draftId = createDraft(titleId);
        when(conflictingJobChecker.isAnyRunning()).thenReturn(true);

        // when, then
        mockMvc.perform(post(BASE + "/drafts/apply").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"draftIds\":[\"" + draftId + "\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LLM_APPLY_CONFLICT"));
        mockMvc.perform(post(BASE + "/titles/" + titleId + "/overview-revert").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LLM_APPLY_CONFLICT"));
        assertThat(jdbcTemplate.queryForObject("SELECT overview_lang FROM title WHERE id = ?", String.class, titleId))
                .isEqualTo("en");
    }

    @Test
    void 영어로_되돌리면_번역의_줄거리와_언어와_번역_시각이_비워지고_번역이_아니면_409이다() throws Exception {
        // given: 관리자 번역, TMDB가 준 한국어(번역 표시 없음), 영어
        long translatedId = insertTranslatedTitle(880021, 1);
        long tmdbKoreanId = insertTitle(880023, 1.0, "ko", "TMDB가 준 한국어 줄거리");
        long englishId = insertTitle(880022, 1.0, "en", ORIGINAL);

        // when, then: 번역은 되돌려진다
        mockMvc.perform(post(BASE + "/titles/" + translatedId + "/overview-revert").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.titleId").value(translatedId));
        assertThat(isOverviewCleared(translatedId)).isTrue();
        // TMDB 한국어는 번역이 아니라 409이고 줄거리가 그대로다
        mockMvc.perform(post(BASE + "/titles/" + tmdbKoreanId + "/overview-revert").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LLM_NOT_REVERTIBLE"));
        assertThat(jdbcTemplate.queryForObject("SELECT overview FROM title WHERE id = ?", String.class, tmdbKoreanId))
                .isEqualTo("TMDB가 준 한국어 줄거리");
        mockMvc.perform(post(BASE + "/titles/" + englishId + "/overview-revert").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LLM_NOT_REVERTIBLE"));
        mockMvc.perform(post(BASE + "/titles/999999999/overview-revert").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 실패한_초안을_고쳐_통과하면_성공으로_바뀌고_수정_표시가_붙어_반영된다() throws Exception {
        // given: 영어가 그대로 돌아온 실패 초안
        when(geminiClient.translateToKorean(anyString())).thenReturn("A detective hunts a killer in a quiet town.");
        long titleId = insertTitle(880101, 1.0, "en", ORIGINAL);
        String draftId = createDraft(titleId);
        String edited = "한 형사가 조용한 해안 마을에서 살인범을 직접 쫓는다.";

        // when: 관리자가 번역문을 고친다
        mockMvc.perform(patch(BASE + "/drafts/" + draftId).header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"translatedText\":\"" + edited + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftId").value(draftId))
                .andExpect(jsonPath("$.data.translated").value(edited))
                .andExpect(jsonPath("$.data.isGuardPassed").value(true))
                .andExpect(jsonPath("$.data.guardFailures", hasSize(0)))
                .andExpect(jsonPath("$.data.isEdited").value(true));

        // then: 목록에서 성공 초안으로 보이고, allPassed 반영 대상이 되어 고친 글이 DB에 들어간다
        mockMvc.perform(get(BASE + "/drafts").param("status", "PASSED").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(jsonPath("$.data.content[0].draftId").value(draftId))
                .andExpect(jsonPath("$.data.content[0].isEdited").value(true));
        mockMvc.perform(post(BASE + "/drafts/apply").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allPassed\":true}"))
                .andExpect(jsonPath("$.data.results[0].outcome").value("APPLIED"));
        assertThat(jdbcTemplate.queryForObject("SELECT overview FROM title WHERE id = ?", String.class, titleId))
                .isEqualTo(edited);
    }

    @Test
    void 고쳐도_실패가_남으면_저장은_되지만_반영은_GUARD_FAILED로_거절된다() throws Exception {
        // given
        long titleId = insertTitle(880102, 1.0, "en", ORIGINAL);
        String draftId = createDraft(titleId);

        // when: 영어만 넣어 한글 비율이 모자라는 글로 고친다
        mockMvc.perform(patch(BASE + "/drafts/" + draftId).header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"translatedText\":\"A detective hunts a killer.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isGuardPassed").value(false))
                .andExpect(jsonPath("$.data.isEdited").value(true));

        // then
        mockMvc.perform(post(BASE + "/drafts/apply").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"draftIds\":[\"" + draftId + "\"]}"))
                .andExpect(jsonPath("$.data.results[0].reason").value("GUARD_FAILED"));
        assertThat(jdbcTemplate.queryForObject("SELECT overview_lang FROM title WHERE id = ?", String.class, titleId))
                .isEqualTo("en");
    }

    @Test
    void 고친_초안의_작품은_일괄_번역이_덮어쓰지_않는다() throws Exception {
        // given: 초안을 만들고 관리자가 고친다
        long titleId = insertTitle(880103, 1.0, "en", ORIGINAL);
        String draftId = createDraft(titleId);
        String edited = "한 형사가 조용한 해안 마을에서 살인범을 직접 쫓는다.";
        mockMvc.perform(patch(BASE + "/drafts/" + draftId).header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"translatedText\":\"" + edited + "\"}"))
                .andExpect(status().isOk());

        // when: 같은 작품을 지정해 일괄 번역한다
        mockMvc.perform(post(BASE + "/bulk-drafts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titleIds\":[" + titleId + "]}"))
                .andExpect(status().isAccepted());
        waitBulkFinished();

        // then: 초안은 그대로 고친 글이다
        mockMvc.perform(get(BASE + "/drafts").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(jsonPath("$.data.content[0].draftId").value(draftId))
                .andExpect(jsonPath("$.data.content[0].translated").value(edited))
                .andExpect(jsonPath("$.data.content[0].isEdited").value(true));
    }

    @Test
    void 초안_수정은_번역문이_비었거나_너무_길면_400이고_없는_초안은_404이며_데모는_403이다() throws Exception {
        // given
        long titleId = insertTitle(880104, 1.0, "en", ORIGINAL);
        String draftId = createDraft(titleId);
        String tooLong = "가".repeat(1001);

        // when, then
        mockMvc.perform(patch(BASE + "/drafts/" + draftId).header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"translatedText\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        mockMvc.perform(patch(BASE + "/drafts/" + draftId).header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"translatedText\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        mockMvc.perform(patch(BASE + "/drafts/no-such-draft").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"translatedText\":\"번역\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LLM_DRAFT_NOT_FOUND"));
        mockMvc.perform(patch(BASE + "/drafts/" + draftId)
                        .header(HttpHeaders.AUTHORIZATION, new TestTokens(jwtTokenProvider).demoBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"translatedText\":\"번역\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 초안을_삭제하면_204이고_없는_초안_삭제는_404_LLM_DRAFT_NOT_FOUND이다() throws Exception {
        // given
        long titleId = insertTitle(880031, 1.0, "en", ORIGINAL);
        String draftId = createDraft(titleId);

        // when, then
        mockMvc.perform(delete(BASE + "/drafts/" + draftId).header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(BASE + "/drafts/" + draftId).header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LLM_DRAFT_NOT_FOUND"));
    }

    @Test
    void 키가_없으면_초안_생성은_503_LLM_DISABLED이다() throws Exception {
        // given
        long titleId = insertTitle(880041, 1.0, "en", ORIGINAL);
        when(geminiClient.isEnabled()).thenReturn(false);

        // when, then
        mockMvc.perform(post(BASE + "/drafts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titleId\":" + titleId + "}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("LLM_DISABLED"));
    }

    @Test
    void 호출이_실패하면_502_LLM_CALL_FAILED이다() throws Exception {
        // given
        long titleId = insertTitle(880042, 1.0, "en", ORIGINAL);
        when(geminiClient.translateToKorean(anyString())).thenThrow(new LlmCallFailedException("실패"));

        // when, then
        mockMvc.perform(post(BASE + "/drafts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titleId\":" + titleId + "}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("LLM_CALL_FAILED"));
    }

    @Test
    void 구글_서버가_혼잡하면_초안_생성은_502_LLM_UPSTREAM_BUSY이다() throws Exception {
        // given
        long titleId = insertTitle(880043, 1.0, "en", ORIGINAL);
        when(geminiClient.translateToKorean(anyString())).thenThrow(new LlmUpstreamBusyException("혼잡"));

        // when, then
        mockMvc.perform(post(BASE + "/drafts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titleId\":" + titleId + "}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("LLM_UPSTREAM_BUSY"));
    }

    @Test
    void 같은_작품을_바로_다시_번역하면_429_LLM_TITLE_COOLDOWN이다() throws Exception {
        // given
        long titleId = insertTitle(880043, 1.0, "en", ORIGINAL);
        createDraft(titleId);

        // when, then
        mockMvc.perform(post(BASE + "/drafts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titleId\":" + titleId + "}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("LLM_TITLE_COOLDOWN"));
    }

    @Test
    void 한국어_작품을_번역하려면_409_LLM_NOT_TRANSLATABLE이다() throws Exception {
        // given
        long titleId = insertTitle(880044, 1.0, "ko", TRANSLATED);

        // when, then
        mockMvc.perform(post(BASE + "/drafts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titleId\":" + titleId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LLM_NOT_TRANSLATABLE"));
    }

    @Test
    void 일괄_번역은_202로_접수되고_끝나면_완료_상태와_초안이_목록에_보인다() throws Exception {
        // given
        long first = insertTitle(880051, 1.0, "en", ORIGINAL);
        long second = insertTitle(880052, 1.0, "en", ORIGINAL);

        // when
        mockMvc.perform(post(BASE + "/bulk-drafts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titleIds\":[" + first + "," + second + "]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.total").value(2));

        // then: 끝날 때까지 기다린 뒤 완료 상태와 초안 2개를 확인한다
        String state = "";
        for (int i = 0; i < 100 && !"COMPLETED".equals(state); i++) {
            Thread.sleep(100);
            MvcResult result = mockMvc.perform(get(BASE + "/drafts").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                    .andReturn();
            state = json(result).path("data").path("bulkRun").path("state").asText();
        }
        assertThat(state).isEqualTo("COMPLETED");
        mockMvc.perform(get(BASE + "/drafts").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(jsonPath("$.data.bulkRun.doneCount").value(2))
                .andExpect(jsonPath("$.data.bulkRun.failedCount").value(0))
                .andExpect(jsonPath("$.data.content", hasSize(2)));
    }

    @Test
    void 일괄_요청은_titleIds와_count_중_하나만_받는다() throws Exception {
        mockMvc.perform(post(BASE + "/bulk-drafts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titleIds\":[1],\"count\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        mockMvc.perform(post(BASE + "/bulk-drafts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 대량_되돌리기에_기준_시각이_없으면_번역_전부를_되돌리고_TMDB_한국어와_영어는_건드리지_않으며_상세_캐시를_지운다() throws Exception {
        // given: 번역 2건(오래된 것과 최근 것), TMDB 한국어, 영어. 번역 하나의 상세를 조회해 캐시에 한국어를 채워 둔다
        long oldTranslated = insertTranslatedTitle(880071, 200);
        long recentTranslated = insertTranslatedTitle(880072, 10);
        long tmdbKorean = insertTitle(880073, 1.0, "ko", "TMDB가 준 한국어 줄거리");
        long english = insertTitle(880074, 1.0, "en", ORIGINAL);
        mockMvc.perform(get("/api/public/titles/movie/880071"))
                .andExpect(jsonPath("$.data.overview").value(TRANSLATED));
        long expectedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM title WHERE overview_translated_at IS NOT NULL", Long.class);

        // when
        mockMvc.perform(post(BASE + "/overview-reverts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revertedCount").value(expectedCount));

        // then: 번역만 비워지고, TMDB 한국어와 영어는 그대로이며, 캐시도 지워져 상세가 새 상태를 보여 준다
        assertThat(expectedCount).isGreaterThanOrEqualTo(2);
        assertThat(isOverviewCleared(oldTranslated)).isTrue();
        assertThat(isOverviewCleared(recentTranslated)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT overview FROM title WHERE id = ?", String.class, tmdbKorean))
                .isEqualTo("TMDB가 준 한국어 줄거리");
        assertThat(jdbcTemplate.queryForObject("SELECT overview FROM title WHERE id = ?", String.class, english))
                .isEqualTo(ORIGINAL);
        mockMvc.perform(get("/api/public/titles/movie/880071"))
                .andExpect(jsonPath("$.data.overview").value(nullValue()));
    }

    @Test
    void 대량_되돌리기를_미리보기로_하면_대상_수만_돌려주고_줄거리와_캐시를_바꾸지_않는다() throws Exception {
        // given: 번역 2건. 하나의 상세를 조회해 상세 캐시에 한국어를 채워 둔다
        long oldTranslated = insertTranslatedTitle(880111, 200);
        long recentTranslated = insertTranslatedTitle(880112, 10);
        mockMvc.perform(get("/api/public/titles/movie/880111"))
                .andExpect(jsonPath("$.data.overview").value(TRANSLATED));
        long expectedAll = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM title WHERE overview_translated_at IS NOT NULL", Long.class);
        String translatedBefore = OffsetDateTime.now().minusDays(100).toString();

        // when, then: 기준 시각 없이 미리보기하면 번역 전부의 수, 기준 시각이 있으면 오래된 것만 센다
        mockMvc.perform(post(BASE + "/overview-reverts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isPreview\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revertedCount").value(expectedAll))
                .andExpect(jsonPath("$.data.isPreview").value(true));
        MvcResult dated = mockMvc.perform(post(BASE + "/overview-reverts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"translatedBefore\":\"" + translatedBefore + "\",\"isPreview\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json(dated).path("data").path("revertedCount").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(json(dated).path("data").path("revertedCount").asLong()).isLessThan(expectedAll + 1);

        // then: DB도 캐시도 그대로다. 실제 실행 응답은 isPreview이 false다
        assertThat(isOverviewCleared(oldTranslated)).isFalse();
        assertThat(isOverviewCleared(recentTranslated)).isFalse();
        mockMvc.perform(get("/api/public/titles/movie/880111"))
                .andExpect(jsonPath("$.data.overview").value(TRANSLATED));
        mockMvc.perform(post(BASE + "/overview-reverts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isPreview\":false}"))
                .andExpect(jsonPath("$.data.isPreview").value(false));
        assertThat(isOverviewCleared(oldTranslated)).isTrue();
    }

    @Test
    void 수집이_실행_중이면_미리보기도_409_LLM_APPLY_CONFLICT이다() throws Exception {
        // given
        when(conflictingJobChecker.isAnyRunning()).thenReturn(true);

        // when, then
        mockMvc.perform(post(BASE + "/overview-reverts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isPreview\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LLM_APPLY_CONFLICT"));
    }

    @Test
    void 대량_되돌리기에_기준_시각이_있으면_그보다_오래된_번역만_되돌린다() throws Exception {
        // given: 200일 전 번역과 10일 전 번역
        long oldTranslated = insertTranslatedTitle(880081, 200);
        long recentTranslated = insertTranslatedTitle(880082, 10);
        String translatedBefore = OffsetDateTime.now().minusDays(100).toString();

        // when
        MvcResult result = mockMvc.perform(post(BASE + "/overview-reverts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"translatedBefore\":\"" + translatedBefore + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        // then: 오래된 번역만 비워지고 최근 번역은 번역 표시와 함께 남는다
        assertThat(json(result).path("data").path("revertedCount").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(isOverviewCleared(oldTranslated)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT overview FROM title WHERE id = ?", String.class, recentTranslated))
                .isEqualTo(TRANSLATED);
        assertThat(jdbcTemplate.queryForObject("SELECT overview_translated_at IS NOT NULL FROM title WHERE id = ?",
                Boolean.class, recentTranslated)).isTrue();
    }

    @Test
    void 대량_되돌리기의_대상이_없으면_되돌린_작품_수가_0이다() throws Exception {
        // given: 아주 먼 과거 기준이라 어떤 번역도 그보다 오래되지 않는다
        long translated = insertTranslatedTitle(880091, 1);

        // when, then
        mockMvc.perform(post(BASE + "/overview-reverts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"translatedBefore\":\"2000-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revertedCount").value(0));
        assertThat(isOverviewCleared(translated)).isFalse();
    }

    @Test
    void 수집이_실행_중이면_대량_되돌리기는_409_LLM_APPLY_CONFLICT이고_줄거리를_바꾸지_않는다() throws Exception {
        // given
        long translated = insertTranslatedTitle(880092, 1);
        when(conflictingJobChecker.isAnyRunning()).thenReturn(true);

        // when, then
        mockMvc.perform(post(BASE + "/overview-reverts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LLM_APPLY_CONFLICT"));
        assertThat(isOverviewCleared(translated)).isFalse();
    }

    @Test
    void 대량_되돌리기에_본문이_없으면_400이다() throws Exception {
        mockMvc.perform(post(BASE + "/overview-reverts").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void 요약에_번역_작품_수와_가장_오래된_번역_시각이_들어_있다() throws Exception {
        // given: 200일 전과 10일 전에 반영한 번역
        insertTranslatedTitle(880101, 200);
        insertTranslatedTitle(880102, 10);

        // when
        MvcResult result = mockMvc.perform(get(BASE + "/overview-candidates")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andReturn();

        // then: 개수는 DB의 번역 수와 같고, 가장 오래된 시각은 200일 전보다 늦지 않다
        JsonNode summary = json(result).path("data").path("summary");
        long dbCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM title WHERE overview_translated_at IS NOT NULL", Long.class);
        assertThat(summary.path("translatedCount").asLong()).isEqualTo(dbCount);
        assertThat(summary.path("translatedCount").asLong()).isGreaterThanOrEqualTo(2);
        Instant oldest = OffsetDateTime.parse(summary.path("oldestTranslatedAt").asText()).toInstant();
        assertThat(oldest).isBeforeOrEqualTo(Instant.now().minus(Duration.ofDays(199)));
    }

    @Test
    void 표본은_검사_통과_초안에서만_뽑고_FAILED와_함께_쓰면_400이다() throws Exception {
        // given
        long titleId = insertTitle(880061, 1.0, "en", ORIGINAL);
        createDraft(titleId);

        // when, then
        mockMvc.perform(get(BASE + "/drafts").param("sample", "5").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].isGuardPassed").value(true));
        mockMvc.perform(get(BASE + "/drafts").param("sample", "5").param("status", "FAILED")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private static final String WARNING_TRANSLATED = "한 형사가 조용한 해안 마을에서 살인범을 데 이고 쫓는다.";   // 깨진 띄어쓰기 경고만 있는 번역

    @Test
    void 주의_초안은_WARNING_필터에만_나오고_PASSED_표본_allPassed_대상에서_빠진다() throws Exception {
        // given: 경고만 있는 번역 초안
        when(geminiClient.translateToKorean(anyString())).thenReturn(WARNING_TRANSLATED);
        long titleId = insertTitle(880071, 1.0, "en", ORIGINAL);
        String draftId = createDraft(titleId);

        // when, then: WARNING 필터에는 나오고 경고 코드와 시도 횟수가 보인다
        mockMvc.perform(get(BASE + "/drafts").param("status", "WARNING").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].draftId").value(draftId))
                .andExpect(jsonPath("$.data.content[0].isGuardPassed").value(true))
                .andExpect(jsonPath("$.data.content[0].guardFailures", hasSize(0)))
                .andExpect(jsonPath("$.data.content[0].warnings[0]").value("SUSPICIOUS_SPACING"))
                .andExpect(jsonPath("$.data.content[0].attempts").value(1));
        // PASSED·FAILED 필터와 표본에는 나오지 않는다
        mockMvc.perform(get(BASE + "/drafts").param("status", "PASSED").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(jsonPath("$.data.content", hasSize(0)));
        mockMvc.perform(get(BASE + "/drafts").param("status", "FAILED").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(jsonPath("$.data.content", hasSize(0)));
        mockMvc.perform(get(BASE + "/drafts").param("sample", "5").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(jsonPath("$.data.content", hasSize(0)));
        // 표본은 WARNING과 함께 쓸 수 없다
        mockMvc.perform(get(BASE + "/drafts").param("sample", "5").param("status", "WARNING")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        // allPassed는 성공 초안만 대상이라 주의 초안은 결과에도 나오지 않고 DB도 바뀌지 않는다
        mockMvc.perform(post(BASE + "/drafts/apply").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allPassed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results", hasSize(0)));
        assertThat(jdbcTemplate.queryForObject("SELECT overview_lang FROM title WHERE id = ?", String.class, titleId))
                .isEqualTo("en");
    }

    @Test
    void 주의_초안은_draftIds로_지정하면_승인되어_반영된다() throws Exception {
        // given
        when(geminiClient.translateToKorean(anyString())).thenReturn(WARNING_TRANSLATED);
        long titleId = insertTitle(880072, 1.0, "en", ORIGINAL);
        String draftId = createDraft(titleId);

        // when
        mockMvc.perform(post(BASE + "/drafts/apply").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"draftIds\":[\"" + draftId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedCount").value(1))
                .andExpect(jsonPath("$.data.results[0].outcome").value("APPLIED"));

        // then
        assertThat(jdbcTemplate.queryForObject("SELECT overview FROM title WHERE id = ?", String.class, titleId))
                .isEqualTo(WARNING_TRANSLATED);
    }

    @Test
    void allPassed는_성공_초안만_반영하고_주의_초안은_그대로_남긴다() throws Exception {
        // given: 성공 초안 1개와 주의 초안 1개
        long passedTitle = insertTitle(880073, 1.0, "en", ORIGINAL);
        when(geminiClient.translateToKorean(anyString())).thenReturn(TRANSLATED);
        createDraft(passedTitle);
        long warningTitle = insertTitle(880074, 1.0, "en", ORIGINAL);
        when(geminiClient.translateToKorean(anyString())).thenReturn(WARNING_TRANSLATED);
        createDraft(warningTitle);

        // when
        mockMvc.perform(post(BASE + "/drafts/apply").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allPassed\":true}"))
                .andExpect(jsonPath("$.data.appliedCount").value(1))
                .andExpect(jsonPath("$.data.results", hasSize(1)));

        // then: 성공 작품만 한국어가 되고 주의 초안은 목록에 남아 있다
        assertThat(jdbcTemplate.queryForObject("SELECT overview_lang FROM title WHERE id = ?", String.class, passedTitle))
                .isEqualTo("ko");
        assertThat(jdbcTemplate.queryForObject("SELECT overview_lang FROM title WHERE id = ?", String.class, warningTitle))
                .isEqualTo("en");
        mockMvc.perform(get(BASE + "/drafts").param("status", "WARNING").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    void 일괄_번역에서_주의가_계속되면_한_번_다시_번역하고_주의_수와_시도_횟수를_남긴다() throws Exception {
        // given: 언제나 경고만 있는 번역
        when(geminiClient.translateToKorean(anyString())).thenReturn(WARNING_TRANSLATED);
        long titleId = insertTitle(880075, 1.0, "en", ORIGINAL);

        // when
        mockMvc.perform(post(BASE + "/bulk-drafts").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titleIds\":[" + titleId + "]}"))
                .andExpect(status().isAccepted());
        String state = "";
        for (int i = 0; i < 100 && !"COMPLETED".equals(state); i++) {
            Thread.sleep(100);
            MvcResult result = mockMvc.perform(get(BASE + "/drafts").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                    .andReturn();
            state = json(result).path("data").path("bulkRun").path("state").asText();
        }

        // then: 첫 시도 + 재시도 1번 = 2회, 주의 1건
        assertThat(state).isEqualTo("COMPLETED");
        mockMvc.perform(get(BASE + "/drafts").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(jsonPath("$.data.bulkRun.doneCount").value(1))
                .andExpect(jsonPath("$.data.bulkRun.warningCount").value(1))
                .andExpect(jsonPath("$.data.bulkRun.guardFailedCount").value(0))
                .andExpect(jsonPath("$.data.content[0].attempts").value(2));
    }
}

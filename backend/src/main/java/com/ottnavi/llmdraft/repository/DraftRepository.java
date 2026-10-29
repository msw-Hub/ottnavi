package com.ottnavi.llmdraft.repository;

import com.ottnavi.llmdraft.config.LlmDraftProperties;
import com.ottnavi.llmdraft.service.model.DraftRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 번역 초안을 Redis에 보관한다(DB 표 없음). 작품당 초안은 1개이고 새로 만들면 옛 초안을 교체한다.
 * 키:
 * <ul>
 *   <li>llm:draft:{draftId} STRING(JSON), TTL = app.llm.draft.ttl — 초안 본문</li>
 *   <li>llm:draft-title:{titleId} STRING(draftId), 같은 TTL — 작품 → 초안 찾기(작품당 1개 보장)</li>
 *   <li>llm:drafts ZSET(member = draftId, score = 만든 시각 epoch ms) — 목록용 색인. TTL이 없어 만료된 초안의 항목이 남을 수 있고,
 *       목록을 읽을 때 본문이 없는 항목을 지운다</li>
 * </ul>
 * 운영 Redis는 persistence가 없고 allkeys-lru라 초안이 언제든 사라질 수 있다. 없는 초안은 오류가 아니라 정상 상황으로 다룬다.
 */
@Slf4j
@Component
public class DraftRepository {

    private static final String DRAFT_PREFIX = "llm:draft:";
    private static final String TITLE_PREFIX = "llm:draft-title:";
    private static final String INDEX_KEY = "llm:drafts";

    private final StringRedisTemplate redis;
    private final LlmDraftProperties properties;

    // 알 수 없는 필드는 무시한다(나중 버전이 필드를 더해도 옛 초안을 읽을 수 있게)
    private final ObjectMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)   // 나중에 더한 숫자 필드(attempts, warningCount)가 없는 옛 JSON을 0으로 읽는다(Jackson 3은 기본이 오류)
            .build();

    public DraftRepository(StringRedisTemplate redis, LlmDraftProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /** 초안을 저장한다. 같은 작품의 옛 초안이 있으면 먼저 지워 작품당 1개를 지킨다 */
    public void save(DraftRecord draft) {
        // 1. 같은 작품의 옛 초안을 지운다
        Optional<String> oldDraftId = findDraftIdByTitle(draft.titleId());
        if (oldDraftId.isPresent() && !oldDraftId.get().equals(draft.draftId())) {
            delete(oldDraftId.get());
        }
        // 2. 본문과 작품 → 초안 연결을 TTL과 함께 저장한다
        redis.opsForValue().set(DRAFT_PREFIX + draft.draftId(), mapper.writeValueAsString(draft),
                properties.draft().ttl());
        redis.opsForValue().set(TITLE_PREFIX + draft.titleId(), draft.draftId(), properties.draft().ttl());
        // 3. 목록 색인에 올린다
        redis.opsForZSet().add(INDEX_KEY, draft.draftId(), draft.createdAt().toEpochMilli());
    }

    /** 초안 ID로 초안을 찾는다. 사라졌거나 읽을 수 없으면 비어 있다 */
    public Optional<DraftRecord> findById(String draftId) {
        return read(redis.opsForValue().get(DRAFT_PREFIX + draftId));
    }

    /** 작품의 초안 ID를 찾는다. 연결이 있어도 초안 본문이 사라졌으면 없는 것으로 본다 */
    public Optional<String> findDraftIdByTitle(Long titleId) {
        String draftId = redis.opsForValue().get(TITLE_PREFIX + titleId);
        if (draftId == null || !Boolean.TRUE.equals(redis.hasKey(DRAFT_PREFIX + draftId))) {
            return Optional.empty();
        }
        return Optional.of(draftId);
    }

    /** 작품의 초안을 찾는다. 없으면 비어 있다 */
    public Optional<DraftRecord> findByTitle(Long titleId) {
        Optional<String> draftId = findDraftIdByTitle(titleId);
        if (draftId.isEmpty()) {
            return Optional.empty();
        }
        return findById(draftId.get());
    }

    /** 작품에 초안이 있는지 */
    public boolean existsByTitle(Long titleId) {
        return findDraftIdByTitle(titleId).isPresent();
    }

    /**
     * 초안을 지운다. 지울 초안이 있었으면 true다. 작품 연결은 이 초안을 가리킬 때만 지운다
     * (그 사이 같은 작품의 새 초안이 만들어졌다면 새 초안의 연결을 건드리지 않는다).
     */
    public boolean delete(String draftId) {
        Optional<DraftRecord> found = findById(draftId);
        boolean existed = found.isPresent();
        redis.delete(DRAFT_PREFIX + draftId);
        redis.opsForZSet().remove(INDEX_KEY, draftId);
        if (existed) {
            String titleKey = TITLE_PREFIX + found.get().titleId();
            if (draftId.equals(redis.opsForValue().get(titleKey))) {
                redis.delete(titleKey);
            }
        }
        return existed;
    }

    /** 색인에 올라 있는 초안 수(만료됐지만 색인에 남은 항목을 포함할 수 있는 근사값) */
    public long countIndexed() {
        Long count = redis.opsForZSet().zCard(INDEX_KEY);
        if (count == null) {
            return 0;
        }
        return count;
    }

    /** 살아 있는 초안을 모두 읽는다(만든 시각 최신순). 본문이 사라진 색인 항목은 이때 지운다 */
    public List<DraftRecord> findAll() {
        // 1. 색인을 최신순으로 읽는다
        Set<String> ids = redis.opsForZSet().reverseRange(INDEX_KEY, 0, -1);
        List<DraftRecord> drafts = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return drafts;
        }
        // 2. 본문을 한 번에 읽는다
        List<String> draftIds = new ArrayList<>(ids);
        List<String> keys = new ArrayList<>();
        for (String draftId : draftIds) {
            keys.add(DRAFT_PREFIX + draftId);
        }
        List<String> values = redis.opsForValue().multiGet(keys);
        if (values == null) {
            return drafts;
        }
        // 3. 읽은 것은 모으고, 사라졌거나 못 읽는 것은 색인에서 지운다
        for (int i = 0; i < draftIds.size(); i++) {
            Optional<DraftRecord> draft = read(values.get(i));
            if (draft.isPresent()) {
                drafts.add(draft.get());
            } else {
                redis.opsForZSet().remove(INDEX_KEY, draftIds.get(i));
            }
        }
        return drafts;
    }

    private Optional<DraftRecord> read(String json) {
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json, DraftRecord.class));
        } catch (JacksonException e) {
            log.warn("번역 초안 JSON을 읽지 못해 없는 것으로 본다: error={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}

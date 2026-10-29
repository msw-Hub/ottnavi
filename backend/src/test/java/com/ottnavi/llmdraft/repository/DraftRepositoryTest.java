package com.ottnavi.llmdraft.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.llmdraft.service.model.DraftGrade;
import com.ottnavi.llmdraft.service.model.DraftRecord;
import com.ottnavi.support.IntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 초안 Redis 보관: 작품당 1개 교체, 삭제, 사라진 초안은 목록에서 조용히 빠지고 색인도 정리된다 (실제 Redis) */
class DraftRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private DraftRepository draftRepository;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    @AfterEach
    void 초안_키를_지운다() {
        Set<String> keys = redis.keys("llm:draft*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private DraftRecord draft(String draftId, long titleId, long epochMilli) {
        return new DraftRecord(draftId, titleId, "movie", "작품" + titleId, "original", "번역", true, List.of(),
                List.of(), "model", Instant.ofEpochMilli(epochMilli), 1);
    }

    @Test
    void 저장한_초안을_ID로_읽을_수_있고_TTL이_붙는다() {
        // given
        DraftRecord saved = draft("d1", 1L, 1_000);

        // when
        draftRepository.save(saved);

        // then
        assertThat(draftRepository.findById("d1")).contains(saved);
        assertThat(redis.getExpire("llm:draft:d1")).isPositive();
    }

    @Test
    void 같은_작품에_새_초안을_저장하면_옛_초안은_사라진다() {
        // given
        draftRepository.save(draft("old", 1L, 1_000));

        // when
        draftRepository.save(draft("new", 1L, 2_000));

        // then
        assertThat(draftRepository.findById("old")).isEmpty();
        assertThat(draftRepository.findById("new")).isPresent();
        assertThat(draftRepository.findDraftIdByTitle(1L)).contains("new");
        assertThat(draftRepository.findAll()).hasSize(1);
    }

    @Test
    void 목록은_만든_시각_최신순이다() {
        // given
        draftRepository.save(draft("a", 1L, 1_000));
        draftRepository.save(draft("b", 2L, 3_000));
        draftRepository.save(draft("c", 3L, 2_000));

        // when
        List<DraftRecord> all = draftRepository.findAll();

        // then
        assertThat(all).extracting("draftId").containsExactly("b", "c", "a");
    }

    @Test
    void 삭제하면_true이고_없는_초안_삭제는_false이다() {
        // given
        draftRepository.save(draft("d1", 1L, 1_000));

        // when, then
        assertThat(draftRepository.delete("d1")).isTrue();
        assertThat(draftRepository.delete("d1")).isFalse();
        assertThat(draftRepository.existsByTitle(1L)).isFalse();
    }

    @Test
    void Redis에서_본문이_사라진_초안은_목록에서_조용히_빠지고_색인도_정리된다() {
        // given: 메모리 부족(allkeys-lru)으로 본문 키만 지워진 상황을 흉내 낸다
        draftRepository.save(draft("d1", 1L, 1_000));
        draftRepository.save(draft("d2", 2L, 2_000));
        redis.delete("llm:draft:d1");
        assertThat(draftRepository.countIndexed()).isEqualTo(2);

        // when
        List<DraftRecord> all = draftRepository.findAll();

        // then
        assertThat(all).extracting("draftId").containsExactly("d2");
        assertThat(draftRepository.countIndexed()).isEqualTo(1);
        assertThat(draftRepository.existsByTitle(1L)).isFalse();
    }

    @Test
    void warnings와_attempts가_없는_옛_JSON도_읽히고_기본값은_경고_없음과_시도_1회이다() {
        // given: 현재 형식으로 저장한 JSON에서 이 필드들을 뺀다(이 필드가 생기기 전에 저장된 초안과 같다)
        draftRepository.save(new DraftRecord("old1", 7L, "movie", "옛 작품", "original", "번역", true, List.of(),
                List.of("SUSPICIOUS_SPACING"), "model", Instant.ofEpochMilli(1_000), 3));
        ObjectNode node = (ObjectNode) JsonMapper.builder().build().readTree(redis.opsForValue().get("llm:draft:old1"));
        node.remove("warnings");
        node.remove("attempts");
        redis.opsForValue().set("llm:draft:old1", node.toString());

        // when
        DraftRecord read = draftRepository.findById("old1").orElseThrow();

        // then
        assertThat(read.warnings()).isEmpty();
        assertThat(read.attempts()).isEqualTo(1);
        assertThat(read.isGuardPassed()).isTrue();
        assertThat(read.grade()).isEqualTo(DraftGrade.PASSED);
    }

    @Test
    void 경고와_시도_횟수가_있는_초안은_저장하고_읽어도_그대로이다() {
        // given
        DraftRecord saved = new DraftRecord("w1", 1L, "movie", "작품", "original", "번역", true, List.of(),
                List.of("SUSPICIOUS_SPACING"), "model", Instant.ofEpochMilli(1_000), 2);

        // when
        draftRepository.save(saved);

        // then
        assertThat(draftRepository.findById("w1")).contains(saved);
        assertThat(draftRepository.findById("w1").orElseThrow().grade()).isEqualTo(DraftGrade.WARNING);
    }
}

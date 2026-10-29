package com.ottnavi.llmdraft.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.llmdraft.service.model.BulkRunState;
import com.ottnavi.llmdraft.service.model.BulkRunStatus;
import com.ottnavi.support.IntegrationTestSupport;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 일괄 번역 진행 상황 Redis 보관: 저장·읽기, warningCount가 없는 옛 JSON 호환 (실제 Redis) */
class BulkRunStatusRepositoryTest extends IntegrationTestSupport {

    private static final String KEY = "llm:bulk:latest";

    @Autowired
    private BulkRunStatusRepository repository;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    @AfterEach
    void 키를_지운다() {
        redis.delete(KEY);
    }

    @Test
    void 저장한_진행_상황을_주의_수까지_그대로_읽는다() {
        // given
        BulkRunStatus saved = new BulkRunStatus("bulk-1", BulkRunState.COMPLETED, 10, 9, 1, 2,
                null, Instant.parse("2026-10-29T01:00:00Z"), Instant.parse("2026-10-29T01:10:00Z"), 4);

        // when
        repository.save(saved);

        // then
        assertThat(repository.find()).contains(saved);
    }

    @Test
    void warningCount가_없는_옛_JSON도_0으로_읽힌다() {
        // given: 현재 형식으로 저장한 JSON에서 warningCount를 뺀다
        repository.save(new BulkRunStatus("bulk-1", BulkRunState.COMPLETED, 10, 9, 1, 2,
                null, Instant.parse("2026-10-29T01:00:00Z"), Instant.parse("2026-10-29T01:10:00Z"), 4));
        ObjectNode node = (ObjectNode) JsonMapper.builder().build().readTree(redis.opsForValue().get(KEY));
        node.remove("warningCount");
        redis.opsForValue().set(KEY, node.toString());

        // when
        BulkRunStatus read = repository.find().orElseThrow();

        // then
        assertThat(read.warningCount()).isEqualTo(0);
        assertThat(read.doneCount()).isEqualTo(9);
    }
}

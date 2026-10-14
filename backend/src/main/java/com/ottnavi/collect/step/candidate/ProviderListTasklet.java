package com.ottnavi.collect.step.candidate;

import com.ottnavi.infra.tmdb.TmdbClient;
import com.ottnavi.infra.tmdb.TmdbMediaType;
import com.ottnavi.infra.tmdb.dto.TmdbProviderInfo;
import com.ottnavi.infra.tmdb.dto.TmdbProviderListResponse;
import com.ottnavi.provider.service.ProviderCollectService;
import com.ottnavi.provider.service.model.DiscoveredProvider;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/** Step 1: TMDB KR 제공처 전체 목록을 받아 처음 보는 제공처를 UNMAPPED로 저장한다. 우리 7곳이 아닌 제공처도 버리지 않는다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderListTasklet implements Tasklet {

    private static final int MAX_NAME_LENGTH = 100; // tmdb_provider.tmdb_name 컬럼 길이

    private final TmdbClient tmdbClient;
    private final ProviderCollectService providerCollectService;

    /** 영화·드라마 제공처 목록을 합쳐 저장한다. 쿠팡플레이는 드라마 목록에만 있어 두 목록을 모두 본다 */
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // 1. 제공처 목록을 모을 공간
        // 외부 호출을 먼저 끝내고 DB 반영만 서비스 트랜잭션으로 묶는다
        Map<Integer, DiscoveredProvider> merged = new LinkedHashMap<>();
        // 2. 영화와 드라마 제공처 목록을 각각 조회
        for (TmdbMediaType mediaType : TmdbMediaType.values()) {
            TmdbProviderListResponse response = tmdbClient.listProviders(mediaType);
            // 3. 응답이 없으면 다음 매체로
            if (response == null || response.results() == null) {
                continue;
            }
            // 4. 이름이 있는 제공처만 번호별로 모은다(두 목록에 겹치면 먼저 본 것을 쓴다)
            for (TmdbProviderInfo info : response.results()) {
                if (info.providerName() == null || info.providerName().isBlank()) {
                    continue; // tmdb_name이 NOT NULL이라 이름 없는 제공처는 저장할 수 없다
                }
                String name = info.providerName().length() <= MAX_NAME_LENGTH
                        ? info.providerName() : info.providerName().substring(0, MAX_NAME_LENGTH);
                merged.putIfAbsent(info.providerId(), new DiscoveredProvider(info.providerId(), name, info.logoPath()));
            }
        }
        // 5. 처음 발견한 제공처만 DB에 저장
        int saved = providerCollectService.registerDiscovered(List.copyOf(merged.values()), Instant.now());
        contribution.incrementWriteCount(saved);
        log.info("제공처 목록 수집: 받은 {}곳 중 새로 저장 {}곳", merged.size(), saved);
        return RepeatStatus.FINISHED;
    }
}

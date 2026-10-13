package com.ottnavi.collect;

import com.ottnavi.catalog.application.CandidateTitle;
import com.ottnavi.provider.application.DiscoverTarget;
import com.ottnavi.provider.application.ProviderCollectService;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/**
 * Step 2·3의 Reader: 서비스 7곳을 차례로 돌며 discover 결과를 마지막 쪽까지 한 건씩 돌려준다.
 * 진행 위치를 저장하지 않는다. Step이 실패하면 처음부터 다시 읽고, 이미 저장된 작품은 Writer가 건너뛰므로 결과는 같다.
 */
@Slf4j
public class CandidateReader implements ItemStreamReader<CandidateTitle> {

    /** TMDB discover는 500쪽(1만 건)까지만 준다 */
    static final int MAX_PAGE = 500;

    /** 한 쪽을 가져오는 함수 */
    @FunctionalInterface
    public interface PageSource {
        CandidatePage fetch(String withWatchProviders, int page);
    }

    /** 한 쪽의 결과: 후보 목록과 전체 쪽 수 */
    public record CandidatePage(List<CandidateTitle> items, int totalPages) {
    }

    private final String label; // 로그용 이름(영화/드라마)
    private final PageSource pageSource;
    private final ProviderCollectService providerCollectService;
    private final CollectPoolMonitor poolMonitor;

    private List<DiscoverTarget> targets = List.of(); // 서비스별 discover 대상
    private int targetIndex; // 지금 읽는 서비스
    private int nextPage; // 그 서비스에서 다음에 읽을 쪽(1부터)
    private final Queue<CandidateTitle> buffer = new ArrayDeque<>(); // 읽어 온 쪽의 남은 작품
    private int maxActive; // Step 중 관찰한 최대 사용 연결 수
    private int pageCount; // 호출한 쪽 수

    public CandidateReader(String label, PageSource pageSource, ProviderCollectService providerCollectService,
                           CollectPoolMonitor poolMonitor) {
        this.label = label;
        this.pageSource = pageSource;
        this.providerCollectService = providerCollectService;
        this.poolMonitor = poolMonitor;
    }

    /** Step이 시작(재시작 포함)할 때마다 처음부터 읽도록 상태를 비운다. ExecutionContext의 옛 값은 보지 않는다 */
    @Override
    public void open(ExecutionContext executionContext) {
        targets = providerCollectService.findDiscoverTargets();
        targetIndex = 0;
        nextPage = 1;
        buffer.clear();
        maxActive = 0;
        pageCount = 0;
    }

    /** 위치를 저장하지 않는다. 저장하면 재시작이 중간부터 이어져 "처음부터 다시" 규칙과 어긋난다 */
    @Override
    public void update(ExecutionContext executionContext) {
        // 일부러 비워 둔다
    }

    @Override
    public void close() {
        log.info("{} 후보 읽기 끝: {}쪽 호출, 연결 풀 최대 사용 {}", label, pageCount, maxActive);
        poolMonitor.log(label + " 후보 읽기 끝");
    }

    /** 다음 작품 한 건을 돌려준다. 모든 서비스를 다 읽었으면 null */
    @Override
    public CandidateTitle read() {
        while (buffer.isEmpty()) {
            if (!fetchNextPage()) {
                return null;
            }
        }
        return buffer.poll();
    }

    /** 다음 쪽을 가져와 buffer에 채운다. 읽을 쪽이 더 없으면 false */
    private boolean fetchNextPage() {
        if (targetIndex >= targets.size()) {
            return false;
        }
        DiscoverTarget target = targets.get(targetIndex);
        CandidatePage page = pageSource.fetch(target.withWatchProviders(), nextPage); // chunk 트랜잭션 안의 TMDB 호출(T-2 예외)
        pageCount++;
        maxActive = Math.max(maxActive, poolMonitor.activeConnections());
        buffer.addAll(page.items());
        if (nextPage >= Math.min(page.totalPages(), MAX_PAGE)) {
            log.info("{} {} discover 끝: {}쪽", label, target.serviceCode(), nextPage);
            targetIndex++;
            nextPage = 1;
        } else {
            nextPage++;
        }
        return true;
    }
}

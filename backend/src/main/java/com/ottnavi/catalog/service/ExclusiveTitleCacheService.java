package com.ottnavi.catalog.service;

import com.ottnavi.catalog.service.model.ExclusiveTitleList;
import com.ottnavi.global.config.RedisConfig;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * 서비스별 독점 목록을 Redis `exclusiveTitles` 캐시에 두고 꺼내 쓴다(Task 078). 테이블은 따로 없다.
 * 수집 Job이 성공으로 끝나면 {@link #refreshAll()}로 미리 계산해 채우고, 실패·정리로 데이터가 바뀌면 {@link #evictAll()}로 비운다.
 * 캐시가 비어 있으면(서버 재시작·TTL 만료·비운 뒤) {@link #get(long)}이 그 자리에서 계산해 채운다(캐시 미스 폴백).
 * 어노테이션 캐시가 아니라 Cache를 직접 쓰므로 CacheErrorHandler가 적용되지 않는다. 그래서 조회 경로({@link #get(long)})는
 * Redis 읽기·쓰기가 실패해도 DB 계산 결과로 응답하도록 여기서 직접 막는다.
 * 클래스에 트랜잭션을 두지 않는다: 계산은 {@link ExclusiveTitleCalculator}가 읽기 전용 트랜잭션으로 하고, Redis 쓰기는 트랜잭션 밖이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExclusiveTitleCacheService {

    private final CacheManager cacheManager;
    private final ExclusiveTitleCalculator calculator;
    private final ReentrantLock missLock = new ReentrantLock(); // 캐시 미스 계산을 한 번에 하나만(서버 한 대 전제)

    /** 서비스 한 곳의 독점 목록을 돌려준다. 캐시에 없으면 7개 서비스를 한꺼번에 계산해 채운 뒤 돌려준다 */
    public ExclusiveTitleList get(long ottServiceId) {
        // 1. 캐시에 있으면 그대로 쓴다
        ExclusiveTitleList cached = readCache(ottServiceId);
        if (cached != null) {
            return cached;
        }

        // 2. 없으면 한 번에 한 요청만 계산한다. 공개 API라 캐시가 빈 순간(재시작·비운 직후)에 요청이 몰리면
        //    요청마다 제공 상태 전체를 읽어 계산하게 되므로(1GB 힙 부담), 줄을 세워 첫 요청이 채우면 나머지는 그 값을 쓴다
        missLock.lock();
        try {
            // 3. 줄 서 있는 동안 앞 요청이 채웠을 수 있으니 다시 확인한다
            cached = readCache(ottServiceId);
            if (cached != null) {
                return cached;
            }

            // 4. 그래도 없으면 즉석에서 계산한다
            log.info("독점 목록 캐시가 비어 있어 즉석에서 계산한다: ottServiceId={}", ottServiceId);
            Map<Long, ExclusiveTitleList> all = calculator.calculateAll();

            // 5. 7곳 모두 캐시에 채운다(Redis 쓰기 실패는 응답에 영향을 주지 않는다)
            try {
                putAll(all);
            } catch (RuntimeException e) {
                log.warn("독점 목록을 캐시에 채우지 못했다(응답은 계산 결과로 나간다): error={}", e.toString());
            }
            return all.get(ottServiceId);
        } finally {
            missLock.unlock();
        }
    }

    /** 캐시에서 서비스 한 곳의 목록을 읽는다. Redis 읽기 실패는 없는 것으로 본다(호출한 쪽이 계산으로 대신한다) */
    private ExclusiveTitleList readCache(long ottServiceId) {
        try {
            return cacheManager.getCache(RedisConfig.EXCLUSIVE_TITLES).get(ottServiceId, ExclusiveTitleList.class);
        } catch (RuntimeException e) {
            log.warn("독점 목록 캐시를 읽지 못해 DB로 계산한다: error={}", e.toString());
            return null;
        }
    }

    /** 7개 서비스의 독점 목록을 새로 계산해 캐시를 덮어쓴다. 수집 Job 성공 뒤에 쓴다. 실패하면 예외를 그대로 올린다 */
    public void refreshAll() {
        // 조회 쪽 미스 계산과 같은 락을 쓴다. 안 그러면 옛 데이터로 계산 중이던 조회가 나중에 옛 값을 덮어써 최대 TTL 동안 남는다
        missLock.lock();
        try {
            putAll(calculator.calculateAll());
        } finally {
            missLock.unlock();
        }
    }

    /**
     * 독점 목록 캐시를 모두 비운다. 비우기에 실패해도 이미 끝난 Job의 결과를 바꾸지 않도록 경고만 남긴다(TTL이 지나면 저절로 새 값이 된다).
     */
    public void evictAll() {
        // 진행 중인 미스 계산이 끝난 뒤에 비운다. 그래야 계산이 옛 값을 다시 채워 넣는 일이 없다
        missLock.lock();
        try {
            cacheManager.getCache(RedisConfig.EXCLUSIVE_TITLES).clear();
        } catch (RuntimeException e) {
            log.warn("독점 목록 캐시를 비우지 못했다(TTL이 지나면 만료된다): error={}", e.toString());
        } finally {
            missLock.unlock();
        }
    }

    private void putAll(Map<Long, ExclusiveTitleList> all) {
        Cache cache = cacheManager.getCache(RedisConfig.EXCLUSIVE_TITLES);
        for (Map.Entry<Long, ExclusiveTitleList> entry : all.entrySet()) {
            cache.put(entry.getKey(), entry.getValue());
        }
    }
}

package com.ottnavi.collect;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DB 연결 풀(HikariCP) 사용량을 읽는다. 수집 chunk 안에서 TMDB를 부르는 예외(TECH T-2) 때문에 연결을 오래 쥐는지 확인하려는 것이다.
 * 같은 값이 Actuator 지표(hikaricp.connections.*)에도 있다. 여기서는 수집 로그에 남기려고 읽는다.
 */
@Slf4j
@Component
public class CollectPoolMonitor {

    private final DataSource dataSource;

    public CollectPoolMonitor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 지금 사용 중인 연결 수를 돌려준다. Hikari가 아니거나 읽을 수 없으면 -1 */
    public int activeConnections() {
        HikariPoolMXBean pool = pool();
        return pool == null ? -1 : pool.getActiveConnections();
    }

    /** 풀 상태를 한 줄 로그로 남긴다. 연결을 기다리는 스레드가 있으면 경고한다 */
    public void log(String label) {
        HikariPoolMXBean pool = pool();
        if (pool == null) {
            return;
        }
        if (pool.getThreadsAwaitingConnection() > 0) {
            log.warn("{}: 연결을 기다리는 스레드가 있다. active={}, total={}, waiting={}",
                    label, pool.getActiveConnections(), pool.getTotalConnections(), pool.getThreadsAwaitingConnection());
        } else {
            log.info("{}: 연결 풀 active={}, idle={}, total={}",
                    label, pool.getActiveConnections(), pool.getIdleConnections(), pool.getTotalConnections());
        }
    }

    private HikariPoolMXBean pool() {
        try {
            if (dataSource.isWrapperFor(HikariDataSource.class)) {
                return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean(); // 풀을 만들기 전이면 null
            }
        } catch (Exception e) {
            log.warn("연결 풀 사용량을 읽을 수 없다. 수집은 계속한다: {}", e.getMessage());
        }
        return null;
    }
}

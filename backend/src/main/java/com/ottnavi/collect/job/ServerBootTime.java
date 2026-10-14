package com.ottnavi.collect.job;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * 이 서버가 기동한 시각. 이보다 먼저 만들어졌는데 아직 실행 중으로 남은 Batch 기록을 "중단됨"으로 판정하는 기준이다.
 * Batch의 CREATE_TIME이 JVM 기본 시간대의 LocalDateTime이라 같은 기준으로 비교하려고 LocalDateTime으로 둔다.
 */
@Component
public class ServerBootTime {

    private final LocalDateTime bootedAt = LocalDateTime.now();

    public LocalDateTime getBootedAt() {
        return bootedAt;
    }
}

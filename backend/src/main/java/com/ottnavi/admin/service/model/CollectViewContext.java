package com.ottnavi.admin.service.model;

import com.ottnavi.collect.job.CollectExecutionRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 수집 실행을 화면 값(상태·재시작 가능 여부)으로 바꿀 때 한 번 만들어 재사용하는 맥락.
 * 요청마다 만들고 저장하지 않는다.
 *
 * @param today             오늘(KST)
 * @param isOverrideAllowed 대상 날짜 직접 지정 허용 여부(local만 true)
 * @param bootedAt          서버 기동 시각(중단됨 판정 기준)
 * @param runningRecords    지금 이 서버에서 실행 중으로 판정된 실행들
 */
public record CollectViewContext(
        LocalDate today,
        boolean isOverrideAllowed,
        LocalDateTime bootedAt,
        List<CollectExecutionRecord> runningRecords) {

    /** 주어진 인스턴스 말고 실행 중인 수집이 있으면 그 실행 중 첫 번째를, 없으면 null을 돌려준다 */
    public CollectExecutionRecord findRunningOtherThan(long instanceId) {
        for (CollectExecutionRecord record : runningRecords) {
            if (record.instanceId() != instanceId) {
                return record;
            }
        }
        return null;
    }
}

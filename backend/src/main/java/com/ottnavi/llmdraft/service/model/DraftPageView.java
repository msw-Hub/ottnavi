package com.ottnavi.llmdraft.service.model;

import java.util.List;

/** 초안 목록 한 페이지와 일괄 진행 상황. 일괄을 한 번도 돌린 적 없으면 bulkRun이 null이다 */
public record DraftPageView(List<DraftRecord> content, int page, int size, long totalElements, int totalPages,
                            BulkRunStatus bulkRun) {
}

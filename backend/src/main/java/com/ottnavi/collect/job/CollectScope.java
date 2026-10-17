package com.ottnavi.collect.job;

/**
 * 수집 Job의 일부만 실행하는 범위(JobParameter scope). 비어 있으면 전체 흐름이다.
 * 이미 모은 데이터에 새 판정 규칙을 다시 적용할 때 6시간 넘는 전체 수집을 피하려는 용도라 로컬에서만 허용한다.
 */
public enum CollectScope {
    AVAILABILITY_TV // 후보·상세 Step을 건너뛰고 드라마 시즌 제공 상태 판정 Step만 실행
}

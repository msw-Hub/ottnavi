package com.ottnavi.collect.job;

import com.ottnavi.collect.step.candidate.CandidateStepConfig;
import com.ottnavi.collect.step.detail.DetailStepConfig;

/**
 * 진행 표시에 쓰는 수집 단계 코드(계약 CollectStepCode). 화면 라벨은 프론트가 붙인다.
 * Batch Step 이름과 1:1이 아니다: 전체 판정(availabilityStep)과 시즌 전용 판정(tvAvailabilityStep)은 둘 다 AVAILABILITY다.
 * 제공처 목록·refresh_tier 재맞춤처럼 몇 초 안에 끝나는 Step은 진행 표시에 넣지 않는다.
 */
public enum CollectStepCode {
    MOVIE_CANDIDATE, // 영화 후보 발견(서비스 7곳 discover)
    TV_CANDIDATE, // 드라마 후보 발견
    MOVIE_DETAIL, // 영화 상세
    TV_DETAIL, // 드라마 상세·시즌
    AVAILABILITY; // 제공 상태 판정

    /** Batch Step 이름을 단계 코드로 바꾼다. 진행 표시에 넣지 않는 Step이면 null이다 */
    public static CollectStepCode fromStepName(String stepName) {
        if (CandidateStepConfig.MOVIE_CANDIDATE_STEP.equals(stepName)) {
            return MOVIE_CANDIDATE;
        }
        if (CandidateStepConfig.TV_CANDIDATE_STEP.equals(stepName)) {
            return TV_CANDIDATE;
        }
        if (DetailStepConfig.MOVIE_DETAIL_STEP.equals(stepName)) {
            return MOVIE_DETAIL;
        }
        if (DetailStepConfig.TV_DETAIL_STEP.equals(stepName)) {
            return TV_DETAIL;
        }
        if (DetailStepConfig.AVAILABILITY_STEP.equals(stepName)
                || DetailStepConfig.TV_AVAILABILITY_STEP.equals(stepName)) {
            return AVAILABILITY;
        }
        return null;
    }
}

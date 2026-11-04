package com.ottnavi.admin.dto.response;

import com.ottnavi.collect.job.CollectProgressRules;
import com.ottnavi.collect.job.CollectStepCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 수집 실행 한 건의 단계별 진행(계약 CollectProgress). 화면은 "completedStepCount/totalStepCount 단계"와 단계별 퍼센트를 보여 준다.
 *
 * @param totalStepCount     그 실행이 거치는 단계 수(전체 5, DAILY 3, 시즌 판정만 1)
 * @param completedStepCount 끝난 단계 수
 * @param currentStep        지금 진행 중인 단계. 진행 중인 단계가 없으면(단계 사이, 끝남, 실패·중단) null
 * @param steps              단계별 진행(실행 순서대로)
 */
public record CollectProgressResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "5") int totalStepCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2") int completedStepCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "MOVIE_DETAIL") CollectStepCode currentStep,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CollectStepProgressResponse> steps) {

	/** 규칙이 만든 실행 진행을 응답으로 바꾼다 */
	public static CollectProgressResponse from(CollectProgressRules.Progress progress) {
		List<CollectStepProgressResponse> steps = new ArrayList<>();
		for (CollectProgressRules.StepProgress step : progress.steps()) {
			steps.add(CollectStepProgressResponse.from(step));
		}
		return new CollectProgressResponse(progress.totalStepCount(), progress.completedStepCount(),
				progress.currentStep(), steps);
	}
}

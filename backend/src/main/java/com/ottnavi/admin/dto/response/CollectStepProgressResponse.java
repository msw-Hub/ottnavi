package com.ottnavi.admin.dto.response;

import com.ottnavi.collect.job.CollectProgressRules;
import com.ottnavi.collect.job.CollectStepCode;
import com.ottnavi.collect.job.CollectStepStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 수집 실행 안의 단계 하나의 진행(계약 CollectStepProgress).
 *
 * @param step                  단계 코드(화면 라벨은 프론트가 붙인다)
 * @param status                단계 상태
 * @param processedCount        읽어 처리한 건수. 진행 중인 상세·판정 단계는 재시작 전 위치를 포함한 처음부터의 수
 * @param totalCount            전체 대상 수. 완료면 처리 수와 같고, 후보 단계·실패·중단·대기는 null
 * @param percent               진행률(0~100 정수, 버림). 후보 단계는 서비스·쪽 진행으로 낸 근사값. 알 수 없으면 null
 * @param completedServiceCount 후보 단계에서 discover를 끝낸 서비스 수. 진행 중인 후보 단계만 값이 있다
 * @param totalServiceCount     후보 단계에서 discover할 서비스 수. 진행 중인 후보 단계만 값이 있다
 */
public record CollectStepProgressResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "MOVIE_DETAIL") CollectStepCode step,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "RUNNING") CollectStepStatus status,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "600") int processedCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "2400") Integer totalCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "25") Integer percent,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Integer completedServiceCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Integer totalServiceCount) {

	/** 규칙이 만든 단계 진행을 응답으로 바꾼다 */
	public static CollectStepProgressResponse from(CollectProgressRules.StepProgress progress) {
		return new CollectStepProgressResponse(progress.step(), progress.status(), progress.processedCount(),
				progress.totalCount(), progress.percent(), progress.completedServiceCount(),
				progress.totalServiceCount());
	}
}

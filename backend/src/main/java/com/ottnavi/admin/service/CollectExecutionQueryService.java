package com.ottnavi.admin.service;

import com.ottnavi.admin.dto.response.CollectExecutionChangeResponse;
import com.ottnavi.admin.dto.response.CollectExecutionResponse;
import com.ottnavi.admin.service.model.CollectViewContext;
import com.ottnavi.catalog.service.AvailabilityChangeQueryService;
import com.ottnavi.catalog.service.model.ExecutionChange;
import com.ottnavi.collect.job.CollectExecutionQuery;
import com.ottnavi.collect.job.CollectExecutionRecord;
import com.ottnavi.collect.job.CollectExecutionRules;
import com.ottnavi.collect.job.CollectExecutionStatus;
import com.ottnavi.collect.job.CollectProperties;
import com.ottnavi.collect.job.ServerBootTime;
import com.ottnavi.global.common.PageResponse;
import com.ottnavi.global.error.ResourceNotFoundException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 실행을 화면 응답으로 바꿔 조회한다(Task 045).
 * 상태·버튼 허용 판정은 시작·재시작 API와 같은 {@link CollectExecutionRules}를 쓴다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CollectExecutionQueryService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final CollectExecutionQuery collectExecutionQuery;
	private final AvailabilityChangeQueryService availabilityChangeQueryService;
	private final CollectProperties collectProperties;
	private final ServerBootTime serverBootTime;

	/** 실행 한 건을 조회한다. 없거나 수집 Job의 실행이 아니면 404이다 */
	public CollectExecutionResponse getExecution(long executionId) {
		// 1. 실행을 찾는다
		Optional<CollectExecutionRecord> found = collectExecutionQuery.findById(executionId);
		if (found.isEmpty()) {
			throw new ResourceNotFoundException("해당 수집 실행을 찾을 수 없습니다.");
		}
		CollectExecutionRecord record = found.get();

		// 2. 한 건짜리 목록으로 바꿔 목록 조회와 같은 경로로 변환한다
		List<CollectExecutionRecord> records = new ArrayList<>();
		records.add(record);
		return toResponses(records).get(0);
	}

	/**
	 * 실행 이력을 대상 날짜 내림차순, 같으면 실행 ID 내림차순으로 한 페이지 돌려준다.
	 * 건수·변경 수·실패 사유는 그 페이지의 실행 ID를 묶어 한 번씩만 조회한다(행 수에 비례한 쿼리 없음).
	 */
	public PageResponse<CollectExecutionResponse> listExecutions(int page, int size) {
		// 1. 전체 건수와 그 페이지의 실행 기록
		long totalElements = collectExecutionQuery.countAll();
		List<CollectExecutionRecord> records = collectExecutionQuery.findPage(page, size);

		// 2. 응답으로 바꾼다(맥락·건수 조회는 toResponses 안에서 요청당 한 번)
		List<CollectExecutionResponse> content = toResponses(records);
		return PageResponse.of(content, page, size, totalElements);
	}

	/** 한 실행에서 제공 상태가 바뀐 작품을 한 페이지 돌려준다. 없는 실행(또는 수집이 아닌 Job)은 404이다 */
	public PageResponse<CollectExecutionChangeResponse> listChanges(long executionId, int page, int size) {
		// 1. 실행이 있는지 확인한다
		if (collectExecutionQuery.findById(executionId).isEmpty()) {
			throw new ResourceNotFoundException("해당 수집 실행을 찾을 수 없습니다.");
		}

		// 2. 변경 한 페이지를 조회한다
		Page<ExecutionChange> found = availabilityChangeQueryService.findByJobExecutionId(executionId, page, size);

		// 3. 응답으로 바꾼다(작품 종류는 API 표기인 소문자로)
		List<CollectExecutionChangeResponse> content = new ArrayList<>();
		for (ExecutionChange change : found.getContent()) {
			content.add(new CollectExecutionChangeResponse(
					change.mediaType().toApiValue(),
					change.tmdbId(),
					change.title(),
					change.seasonNumber(),
					change.ottServiceId(),
					change.beforeStatus(),
					change.afterStatus()));
		}
		return PageResponse.of(content, page, size, found.getTotalElements());
	}

	/**
	 * 실행 여러 건을 응답으로 바꾼다(목록 조회가 재사용한다). 처리 건수·변경 건수·실패 사유는 한 번씩만 묶어 조회한다.
	 */
	public List<CollectExecutionResponse> toResponses(List<CollectExecutionRecord> records) {
		// 1. 요청마다 한 번 맥락(오늘, 기동 시각, 실행 중 목록)을 만든다
		CollectViewContext context = createContext();

		// 2. 실행 ID 목록을 모은다
		List<Long> executionIds = new ArrayList<>();
		for (CollectExecutionRecord record : records) {
			executionIds.add(record.executionId());
		}

		// 3. 처리 건수와 변경 건수를 묶어서 조회한다
		Map<Long, Integer> writeCounts = collectExecutionQuery.sumDetailWriteCounts(executionIds);
		Map<Long, Integer> changeCounts = availabilityChangeQueryService.countByJobExecutionIds(executionIds);

		// 4. Job의 종료 메시지가 빈 실패 실행은 실패한 Step의 메시지로 대체할 수 있게 모아 조회한다
		List<Long> needStepMessageIds = new ArrayList<>();
		for (CollectExecutionRecord record : records) {
			CollectExecutionStatus status = CollectExecutionRules.statusOf(record, context.bootedAt());
			if (status == CollectExecutionStatus.FAILED && isBlank(record.exitMessage())) {
				needStepMessageIds.add(record.executionId());
			}
		}
		Map<Long, String> stepMessages = collectExecutionQuery.findFailedStepExitMessages(needStepMessageIds);

		// 5. 한 건씩 응답으로 바꾼다
		List<CollectExecutionResponse> responses = new ArrayList<>();
		for (CollectExecutionRecord record : records) {
			responses.add(toResponse(record, writeCounts, changeCounts, stepMessages, context));
		}
		return responses;
	}

	/** 판정에 쓰는 맥락을 만든다. 실행 중으로 남은 기록 가운데 이 서버에서 실제로 도는 것만 실행 중 목록에 넣는다 */
	public CollectViewContext createContext() {
		// 1. 오늘(KST)과 설정, 서버 기동 시각
		LocalDate today = LocalDate.now(KST);
		boolean isOverrideAllowed = collectProperties.allowTargetDateOverride();

		// 2. 끝나지 않은 기록 중 이 서버에서 실행 중인 것만 고른다(서버 재기동 전 기록은 중단됨이라 제외)
		List<CollectExecutionRecord> running = new ArrayList<>();
		for (CollectExecutionRecord record : collectExecutionQuery.findUnfinished()) {
			CollectExecutionStatus status = CollectExecutionRules.statusOf(record, serverBootTime.getBootedAt());
			if (status == CollectExecutionStatus.RUNNING) {
				running.add(record);
			}
		}
		return new CollectViewContext(today, isOverrideAllowed, serverBootTime.getBootedAt(), running);
	}

	private CollectExecutionResponse toResponse(CollectExecutionRecord record, Map<Long, Integer> writeCounts,
			Map<Long, Integer> changeCounts, Map<Long, String> stepMessages, CollectViewContext context) {
		// 1. 화면 상태
		CollectExecutionStatus status = CollectExecutionRules.statusOf(record, context.bootedAt());

		// 2. 건수: 기록이 없으면 0
		int processedCount = valueOrZero(writeCounts, record.executionId());
		int changedCount = valueOrZero(changeCounts, record.executionId());

		// 3. 실패 사유: Job 종료 메시지가 비면 실패한 Step의 메시지로 대체한다
		CollectExecutionRecord forReason = record;
		String stepMessage = stepMessages.get(record.executionId());
		if (isBlank(record.exitMessage()) && stepMessage != null) {
			forReason = new CollectExecutionRecord(record.executionId(), record.instanceId(), record.targetDate(),
					record.tier(), record.batchStatus(), record.exitCode(), stepMessage, record.createTime(),
					record.startTime(), record.endTime(), record.isLatestOfInstance());
		}
		String failureReason = CollectExecutionRules.failureReasonOf(forReason, status);

		// 4. 버튼 허용: 시작·재시작 거절 규칙과 같은 판정 코드를 쓴다
		boolean canRecover = CollectExecutionRules.canRecover(status);
		CollectExecutionRules.DateVerdict dateVerdict = CollectExecutionRules.judgeDate(
				record.targetDate(), context.today(), context.isOverrideAllowed());
		boolean isAnotherRunning = context.findRunningOtherThan(record.instanceId()) != null;
		BatchStatus batchStatus = record.batchStatus();
		boolean canRestart = CollectExecutionRules.canRestart(
				status, batchStatus, record.isLatestOfInstance(), dateVerdict, isAnotherRunning);

		// 5. 응답
		return new CollectExecutionResponse(
				record.executionId(),
				record.targetDate(),
				record.tier(),
				status,
				CollectExecutionRules.startedAtOf(record),
				CollectExecutionRules.endedAtOf(record, status),
				processedCount,
				changedCount,
				failureReason,
				canRecover,
				canRestart);
	}

	private static int valueOrZero(Map<Long, Integer> counts, long executionId) {
		Integer value = counts.get(executionId);
		if (value == null) {
			return 0;
		}
		return value;
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}
}

package com.ottnavi.llmdraft.controller;

import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.llmdraft.dto.request.ApplyDraftsRequest;
import com.ottnavi.llmdraft.dto.request.CreateBulkOverviewDraftsRequest;
import com.ottnavi.llmdraft.dto.request.CreateDraftRequest;
import com.ottnavi.llmdraft.dto.request.EditDraftRequest;
import com.ottnavi.llmdraft.dto.request.RevertTranslatedOverviewsRequest;
import com.ottnavi.llmdraft.dto.response.ApplyOverviewDraftsResponse;
import com.ottnavi.llmdraft.dto.response.OverviewCandidatePageResponse;
import com.ottnavi.llmdraft.dto.response.BulkDraftRunResponse;
import com.ottnavi.llmdraft.dto.response.OverviewDraftPageResponse;
import com.ottnavi.llmdraft.dto.response.OverviewDraftResponse;
import com.ottnavi.llmdraft.dto.response.OverviewRevertResponse;
import com.ottnavi.llmdraft.dto.response.RevertTranslatedOverviewsResponse;
import com.ottnavi.llmdraft.service.DraftApplyService;
import com.ottnavi.llmdraft.service.DraftBulkService;
import com.ottnavi.llmdraft.service.DraftEditService;
import com.ottnavi.llmdraft.service.DraftQueryService;
import com.ottnavi.llmdraft.service.DraftService;
import com.ottnavi.llmdraft.service.OverviewCandidateService;
import com.ottnavi.llmdraft.service.model.BulkRunStatus;
import com.ottnavi.llmdraft.service.model.DraftStatusFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 영어 줄거리 한국어 번역 관리자 API. 관리자가 직접 시작하고 검수하며 자동 번역은 없다.
 * 조회(GET)는 ADMIN·DEMO, 쓰기·실행(POST·PATCH·DELETE)은 ADMIN만 가능하다(SecurityConfig의 /api/admin/** 규칙). 배치 토큰은 쓰지 못한다.
 */
@Tag(name = "admin")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/llm")
public class AdminLlmController {

	private final OverviewCandidateService overviewCandidateService;
	private final DraftService draftService;
	private final DraftBulkService draftBulkService;
	private final DraftQueryService draftQueryService;
	private final DraftApplyService draftApplyService;
	private final DraftEditService draftEditService;

	/** 번역 후보(영어 줄거리 작품)를 찜 많은 순 → 인기순으로 조회한다. 응답 요약에 사용량·상한·모델·키 유무가 들어 있다 */
	@Operation(operationId = "listOverviewCandidates", summary = "번역 후보 목록 조회")
	@GetMapping("/overview-candidates")
	public CommonResponse<OverviewCandidatePageResponse> listOverviewCandidates(
			@RequestParam(defaultValue = "0") @Min(value = 0, message = "0 이상이어야 합니다.") int page,
			@RequestParam(defaultValue = "20")
			@Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = 100, message = "100 이하여야 합니다.") int size) {
		return CommonResponse.success(OverviewCandidatePageResponse.from(overviewCandidateService.list(page, size)));
	}

	/** 작품 한 건의 번역 초안을 만든다(이미 있으면 교체). 201과 초안을 돌려준다 */
	@Operation(operationId = "createOverviewDraft", summary = "번역 초안 생성")
	@PostMapping("/drafts")
	@ResponseStatus(HttpStatus.CREATED)
	public CommonResponse<OverviewDraftResponse> createOverviewDraft(@Valid @RequestBody CreateDraftRequest request) {
		return CommonResponse.success(OverviewDraftResponse.from(draftService.createDraft(request.titleId())));
	}

	/** 여러 작품의 번역을 서버 안에서 차례로 시작한다. 202와 일괄 진행 상황을 돌려준다 */
	@Operation(operationId = "createBulkOverviewDrafts", summary = "일괄 번역 시작")
	@PostMapping("/bulk-drafts")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public CommonResponse<BulkDraftRunResponse> createBulkOverviewDrafts(
			@Valid @RequestBody CreateBulkOverviewDraftsRequest request) {
		// 1. titleIds와 count 중 정확히 하나만 받는다
		boolean hasTitleIds = request.titleIds() != null;
		boolean hasCount = request.count() != null;
		if (hasTitleIds == hasCount) {
			throw new FieldValidationException("titleIds", "titleIds와 count 중 하나만 보내야 합니다.");
		}
		// 2. 시작하고 진행 상황을 돌려준다
		BulkRunStatus status;
		if (hasTitleIds) {
			status = draftBulkService.startByTitleIds(request.titleIds());
		} else {
			status = draftBulkService.startByCount(request.count());
		}
		return CommonResponse.success(BulkDraftRunResponse.from(status));
	}

	/** 번역 초안 목록을 조회한다. status(성공·주의·실패)로 거르고, sample이면 성공 초안 중 무작위 N개를 돌려준다 */
	@Operation(operationId = "listOverviewDrafts", summary = "번역 초안 목록 조회")
	@GetMapping("/drafts")
	public CommonResponse<OverviewDraftPageResponse> listOverviewDrafts(
			@RequestParam(required = false) DraftStatusFilter status,
			@RequestParam(required = false)
			@Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = 100, message = "100 이하여야 합니다.") Integer sample,
			@RequestParam(defaultValue = "0") @Min(value = 0, message = "0 이상이어야 합니다.") int page,
			@RequestParam(defaultValue = "20")
			@Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = 100, message = "100 이하여야 합니다.") int size) {
		return CommonResponse.success(OverviewDraftPageResponse.from(draftQueryService.list(status, sample, page, size)));
	}

	/** 초안을 작품 줄거리에 반영한다(건별 결과를 돌려준다). 수집·재계산·정리가 실행 중이면 409 */
	@Operation(operationId = "applyOverviewDrafts", summary = "번역 초안 반영")
	@PostMapping("/drafts/apply")
	public CommonResponse<ApplyOverviewDraftsResponse> applyOverviewDrafts(@Valid @RequestBody ApplyDraftsRequest request) {
		// 1. draftIds와 allPassed 중 정확히 하나만 받는다
		boolean hasDraftIds = request.draftIds() != null;
		boolean isAllPassed = Boolean.TRUE.equals(request.allPassed());
		if (hasDraftIds == isAllPassed) {
			throw new FieldValidationException("draftIds", "draftIds와 allPassed(true) 중 하나만 보내야 합니다.");
		}
		// 2. 반영하고 건별 결과를 돌려준다
		return CommonResponse.success(ApplyOverviewDraftsResponse.from(
				draftApplyService.apply(request.draftIds(), isAllPassed)));
	}

	/** 번역 초안의 번역문을 관리자가 고친다. 자동 검사를 다시 돌려 등급을 새로 정하고 isEdited를 true로 둔다. 없으면 404 LLM_DRAFT_NOT_FOUND */
	@Operation(operationId = "editOverviewDraft", summary = "번역 초안 수정")
	@PatchMapping("/drafts/{draftId}")
	public CommonResponse<OverviewDraftResponse> editOverviewDraft(@PathVariable String draftId,
			@Valid @RequestBody EditDraftRequest request) {
		return CommonResponse.success(OverviewDraftResponse.from(draftEditService.edit(draftId, request.translatedText())));
	}

	/** 번역 초안 한 건을 삭제한다. 없으면 404 LLM_DRAFT_NOT_FOUND */
	@Operation(operationId = "deleteOverviewDraft", summary = "번역 초안 삭제")
	@DeleteMapping("/drafts/{draftId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteOverviewDraft(@PathVariable String draftId) {
		draftApplyService.deleteDraft(draftId);
	}

	/** 관리자가 반영한 번역을 한꺼번에 영어로 되돌린다(전부, 또는 translatedBefore보다 오래된 것만). 다음 수집이 TMDB 영어 줄거리를 다시 채운다. isPreview이면 되돌리지 않고 대상 수만 센다 */
	@Operation(operationId = "revertTranslatedOverviews", summary = "번역 줄거리 대량 되돌리기")
	@PostMapping("/overview-reverts")
	public CommonResponse<RevertTranslatedOverviewsResponse> revertTranslatedOverviews(
			@Valid @RequestBody RevertTranslatedOverviewsRequest request) {
		Instant translatedBefore = null;
		if (request.translatedBefore() != null) {
			translatedBefore = request.translatedBefore().toInstant();
		}
		// isPreview이 true면 DB를 바꾸지 않고 되돌려질 작품 수만 센다
		boolean isPreview = Boolean.TRUE.equals(request.isPreview());
		long revertedCount;
		if (isPreview) {
			revertedCount = draftApplyService.countTranslatedOverviewsToRevert(translatedBefore);
		} else {
			revertedCount = draftApplyService.revertTranslatedOverviews(translatedBefore);
		}
		return CommonResponse.success(new RevertTranslatedOverviewsResponse(revertedCount, isPreview));
	}

	/** 관리자가 반영한 번역 줄거리를 비워 영어로 되돌린다. 다음 수집이 TMDB 영어 줄거리를 다시 채운다 */
	@Operation(operationId = "revertTitleOverview", summary = "줄거리를 영어로 되돌리기")
	@PostMapping("/titles/{titleId}/overview-revert")
	public CommonResponse<OverviewRevertResponse> revertTitleOverview(
			@PathVariable @Min(value = 1, message = "1 이상이어야 합니다.") Long titleId) {
		draftApplyService.revertToEnglish(titleId);
		return CommonResponse.success(new OverviewRevertResponse(titleId));
	}
}

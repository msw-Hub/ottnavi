package com.ottnavi.plan.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/** 초안을 계산한 뒤 데이터 수집이 새로 끝났을 때 던진다. 409 PLAN_DRAFT_STALE로 응답한다 */
public class PlanDraftStaleException extends BusinessException {

	public PlanDraftStaleException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.PLAN_DRAFT_STALE;
	}
}

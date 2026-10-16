package com.ottnavi.plan.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/** 저장하려는 초안이 없거나 본인 것이 아니거나 DRAFT가 아닐 때 던진다. 404 PLAN_DRAFT_NOT_FOUND로 응답한다 */
public class PlanDraftNotFoundException extends BusinessException {

	public PlanDraftNotFoundException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.PLAN_DRAFT_NOT_FOUND;
	}
}

package com.ottnavi.plan.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/** 재계산 또는 수집이 이미 실행 중이어서 재계산을 시작할 수 없을 때 던진다. 409 BATCH_ALREADY_RUNNING으로 응답한다 */
public class PlanRecalcAlreadyRunningException extends BusinessException {

	public PlanRecalcAlreadyRunningException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.BATCH_ALREADY_RUNNING;
	}
}

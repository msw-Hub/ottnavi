package com.ottnavi.admin.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/** 같은 날짜·범위 또는 다른 날짜의 수집이 이미 실행 중일 때 던진다. 409 BATCH_ALREADY_RUNNING로 응답한다 */
public class BatchAlreadyRunningException extends BusinessException {

	public BatchAlreadyRunningException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.BATCH_ALREADY_RUNNING;
	}
}

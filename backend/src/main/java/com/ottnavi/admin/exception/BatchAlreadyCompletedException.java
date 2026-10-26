package com.ottnavi.admin.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/** 같은 날짜·범위의 수집이 이미 완료됐을 때 던진다. 409 BATCH_ALREADY_COMPLETED로 응답한다 */
public class BatchAlreadyCompletedException extends BusinessException {

	public BatchAlreadyCompletedException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.BATCH_ALREADY_COMPLETED;
	}
}

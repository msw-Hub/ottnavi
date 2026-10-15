package com.ottnavi.catalog.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/** 요청한 작품이 DB에 없을 때 던진다. 404 TITLE_NOT_FOUND로 응답한다 */
public class TitleNotFoundException extends BusinessException {

	public TitleNotFoundException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.TITLE_NOT_FOUND;
	}
}

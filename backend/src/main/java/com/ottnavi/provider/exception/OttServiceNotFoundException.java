package com.ottnavi.provider.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/** 요청한 OTT 서비스가 없을 때 던진다. 404 OTT_SERVICE_NOT_FOUND로 응답한다 */
public class OttServiceNotFoundException extends BusinessException {

	public OttServiceNotFoundException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.OTT_SERVICE_NOT_FOUND;
	}
}

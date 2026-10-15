package com.ottnavi.wishlist.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/** 찜 개수가 상한에 도달했을 때 던진다. 409 WISHLIST_LIMIT_EXCEEDED로 응답한다 */
public class WishlistLimitExceededException extends BusinessException {

	public WishlistLimitExceededException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.WISHLIST_LIMIT_EXCEEDED;
	}
}

package com.ottnavi.wishlist.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/** 바꾸려는 찜 항목이 없거나 본인 것이 아닐 때 던진다. 404 WISHLIST_ITEM_NOT_FOUND로 응답한다 */
public class WishlistItemNotFoundException extends BusinessException {

	public WishlistItemNotFoundException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.WISHLIST_ITEM_NOT_FOUND;
	}
}

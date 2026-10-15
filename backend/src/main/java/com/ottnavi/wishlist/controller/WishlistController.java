package com.ottnavi.wishlist.controller;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.global.security.AuthUser;
import com.ottnavi.wishlist.dto.request.AddWishlistItemRequest;
import com.ottnavi.wishlist.dto.request.UpdateWishlistItemRequest;
import com.ottnavi.wishlist.dto.response.AddWishlistItemResultResponse;
import com.ottnavi.wishlist.dto.response.UpdatedWishlistItemResponse;
import com.ottnavi.wishlist.dto.response.WishlistItemListResponse;
import com.ottnavi.wishlist.service.WishlistService;
import com.ottnavi.wishlist.service.model.AddWishlistResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 찜 API. 로그인 사용자 본인의 찜만 다룬다(/api/me/wishlist-items) */
@Tag(name = "wishlist")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/wishlist-items")
public class WishlistController {

	private final WishlistService wishlistService;

	/** 내 찜 전부를 최근에 찜한 순으로 조회한다("다 봤음" 항목 포함) */
	@Operation(operationId = "listWishlistItems", summary = "내 찜 목록 조회")
	@GetMapping
	public CommonResponse<WishlistItemListResponse> listWishlistItems(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser) {
		return CommonResponse.success(WishlistItemListResponse.from(wishlistService.list(authUser.userId())));
	}

	/** 작품(드라마는 시즌)을 찜한다. 새로 만들면 201, 이미 찜한 단위면 기존 항목 ID와 200이다 */
	@Operation(operationId = "addWishlistItem", summary = "찜 추가")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "이미 찜한 작품(시즌). 기존 항목 ID를 돌려준다"),
			@ApiResponse(responseCode = "201", description = "새로 찜함")})
	@PostMapping
	public ResponseEntity<CommonResponse<AddWishlistItemResultResponse>> addWishlistItem(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
			@Valid @RequestBody AddWishlistItemRequest request) {
		AddWishlistResult result = wishlistService.add(authUser.userId(), MediaType.fromApiValue(request.mediaType()),
				request.tmdbId().intValue(), request.seasonNumber(), request.priority());
		HttpStatus status = HttpStatus.OK;
		if (result.isCreated()) {
			status = HttpStatus.CREATED;
		}
		return ResponseEntity.status(status)
				.body(CommonResponse.success(new AddWishlistItemResultResponse(result.wishlistItemId())));
	}

	/** 찜의 우선순위·"다 봤음"을 바꾼다(보낸 필드만) */
	@Operation(operationId = "updateWishlistItem", summary = "찜 우선순위·\"다 봤음\" 변경")
	@PatchMapping("/{wishlistItemId}")
	public CommonResponse<UpdatedWishlistItemResponse> updateWishlistItem(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
			@Positive(message = "1 이상이어야 합니다.") @PathVariable Long wishlistItemId,
			@Valid @RequestBody UpdateWishlistItemRequest request) {
		return CommonResponse.success(UpdatedWishlistItemResponse.from(wishlistService.update(
				authUser.userId(), wishlistItemId, request.priority(), request.isWatched())));
	}

	/** 찜을 삭제한다. 없거나 본인 것이 아니어도 204다 */
	@Operation(operationId = "removeWishlistItem", summary = "찜 삭제")
	@DeleteMapping("/{wishlistItemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeWishlistItem(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
			@Positive(message = "1 이상이어야 합니다.") @PathVariable Long wishlistItemId) {
		wishlistService.remove(authUser.userId(), wishlistItemId);
	}
}

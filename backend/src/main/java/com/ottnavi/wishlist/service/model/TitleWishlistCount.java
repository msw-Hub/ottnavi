package com.ottnavi.wishlist.service.model;

/** 작품 하나를 찜한 사용자 수. 공개 홈의 "찜 많은 작품"이 쓴다("다 봤음" 제외, 작품 단위) */
public record TitleWishlistCount(Long titleId, long userCount) {
}

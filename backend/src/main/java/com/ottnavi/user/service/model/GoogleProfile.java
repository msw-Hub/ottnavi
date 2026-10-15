package com.ottnavi.user.service.model;

/**
 * 구글 로그인으로 받은 프로필 값.
 *
 * @param sub   구글 계정의 고유 번호
 * @param email 이메일
 * @param name  표시 이름(비어 올 수 있다)
 */
public record GoogleProfile(String sub, String email, String name) {
}

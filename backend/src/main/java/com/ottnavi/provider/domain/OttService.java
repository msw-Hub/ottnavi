package com.ottnavi.provider.domain;

import com.ottnavi.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 우리 7개 OTT 서비스(ERD ott_service).
 * 행은 Flyway 시드(V1)로만 만들고 앱에서 생성·수정하지 않으므로 생성자 오버로드와 변경 메서드를 두지 않는다.
 */
@Getter
@Entity
@Table(name = "ott_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OttService extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, unique = true, length = 30)
	private OttServiceCode code; // 서비스 코드

	@Column(nullable = false, length = 50)
	private String name; // 화면 표시 이름

	@Column(nullable = false, length = 200)
	private String logoPath; // TMDB 이미지 상대 경로

	@Column(nullable = false)
	private int displayOrder; // 화면 정렬 순서

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DataQuality dataQuality; // 데이터 충분도, 쿠팡플레이는 INSUFFICIENT
}

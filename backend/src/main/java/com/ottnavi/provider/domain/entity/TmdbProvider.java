package com.ottnavi.provider.domain.entity;

import com.ottnavi.global.common.BaseTimeEntity;
import com.ottnavi.provider.domain.enums.MappingState;
import com.ottnavi.provider.domain.enums.ProviderTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** TMDB 제공처와 우리 서비스의 매핑(ERD tmdb_provider). PK는 TMDB의 provider_id를 그대로 쓴다 */
@Getter
@Entity
@Table(name = "tmdb_provider")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TmdbProvider extends BaseTimeEntity {

    @Id
    private Integer providerId; // TMDB 값 그대로(8, 1796 등). 자동 생성하지 않는다

    @Column(nullable = false, length = 100)
    private String tmdbName; // TMDB 원본 라벨, 관리자 확인용

    @Column(length = 200)
    private String logoPath; // TMDB 이미지 상대 경로

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MappingState mappingState; // UNMAPPED / MAPPED / IGNORED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ott_service_id")
    private OttService ottService; // MAPPED일 때만 값이 있다

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProviderTier tier; // STANDARD / AD

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(nullable = false)
    private Instant firstSeenAt; // 처음 발견된 시각

    /** 새로 발견한 제공처를 UNMAPPED 상태로 만든다 */
    public static TmdbProvider discover(Integer providerId, String tmdbName, String logoPath, Instant firstSeenAt) {
        TmdbProvider provider = new TmdbProvider();
        provider.providerId = providerId;
        provider.tmdbName = tmdbName;
        provider.logoPath = logoPath;
        provider.mappingState = MappingState.UNMAPPED;
        provider.tier = ProviderTier.STANDARD; // DB 기본값과 같다. Hibernate는 DB 기본값을 쓰지 않으므로 직접 채운다
        provider.firstSeenAt = firstSeenAt;
        return provider;
    }

    /** 우리 서비스에 연결한다. DB CHECK(MAPPED이면 서비스가 있어야 함)와 맞추려고 상태와 서비스를 함께 바꾼다 */
    public void mapTo(OttService ottService, ProviderTier tier) {
        if (ottService == null) {
            throw new IllegalArgumentException("연결할 서비스가 없다");
        }
        this.mappingState = MappingState.MAPPED;
        this.ottService = ottService;
        this.tier = tier;
    }

    /**
     * 관리자 화면에서 고른 매핑 상태로 바꾼다.
     * MAPPED면 서비스와 tier를 그대로 저장하고, UNMAPPED·IGNORED면 서비스 연결을 끊고 tier를 STANDARD로 돌린다(계약 updateAdminProvider).
     * 서비스 없는 MAPPED는 DB CHECK 제약 위반이라 여기서 먼저 막는다.
     */
    public void changeMapping(MappingState mappingState, OttService ottService, ProviderTier tier) {
        if (mappingState == MappingState.MAPPED) {
            mapTo(ottService, tier);
            return;
        }
        if (ottService != null) {
            throw new IllegalArgumentException("MAPPED가 아니면 서비스를 연결할 수 없다");
        }
        this.mappingState = mappingState;
        this.ottService = null;
        this.tier = ProviderTier.STANDARD;
    }

    /** 관련 없는 제공처로 표시한다. 서비스 연결은 끊고 tier는 STANDARD로 돌린다 */
    public void ignore() {
        this.mappingState = MappingState.IGNORED;
        this.ottService = null;
        this.tier = ProviderTier.STANDARD;
    }
}

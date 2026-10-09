import { TitleDetailScreen } from '@/features/title-detail/components/TitleDetailScreen'

/**
 * SCR-02 작품 상세 화면(`/titles/:mediaType/:tmdbId`, 공개).
 *
 * 주소에 mediaType(movie·tv)과 tmdbId를 함께 두는 이유: TMDB는 영화와 TV의 ID 공간이 달라 ID만으로는 겹친다
 * (작품은 UNIQUE(media_type, tmdb_id), TASK018_031_PLAN 5절).
 * 주소 값 검증과 데이터 조회·화면 조립은 features/title-detail에 있고, 이 파일은 연결만 한다(pages는 얇게, frontend.md).
 */
export function TitleDetailPage() {
  return <TitleDetailScreen />
}

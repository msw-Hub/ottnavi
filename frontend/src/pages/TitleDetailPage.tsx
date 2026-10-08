import { useParams } from 'react-router'

/**
 * SCR-02 작품 상세 화면(`/titles/:mediaType/:tmdbId`, 공개). 내용은 Task 027(features/title-detail)에서 채운다.
 *
 * 주소에 mediaType(movie·tv)과 tmdbId를 함께 두는 이유: TMDB는 영화와 TV의 ID 공간이 달라 ID만으로는 겹친다
 * (작품은 UNIQUE(media_type, tmdb_id), TASK018_031_PLAN 5절).
 * mediaType 값 검증(movie·tv 외 값이면 404)은 상세 화면을 만들 때 계약의 enum에 맞춰 넣는다.
 */
export function TitleDetailPage() {
  const { mediaType, tmdbId } = useParams()

  return (
    <section className="flex flex-col gap-3">
      <h1 className="text-2xl font-semibold">작품 상세</h1>
      <p className="text-muted-foreground">
        화면 골격입니다. 작품 구분 {mediaType}, TMDB ID {tmdbId}
      </p>
    </section>
  )
}

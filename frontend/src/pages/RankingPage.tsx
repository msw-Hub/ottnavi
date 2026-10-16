import { RankingScreen } from '@/features/home/components/RankingScreen'

/** 랭킹(`/ranking`, 공개, 홈 1차안). 서비스별 인기 순위와 찜 많은 작품. 기준은 ?sort=, 서비스는 ?service=로 받는다. */
export function RankingPage() {
  return <RankingScreen />
}

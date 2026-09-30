import { apiClient } from './client'
import type { LeaderboardEntryResponse, MyRankResponse } from './types'

export const leaderboardApi = {
  getTop: (level: string, limit = 10) =>
    apiClient
      .get<LeaderboardEntryResponse[]>('/exams/leaderboard', { params: { level, limit } })
      .then((r) => r.data),

  getMyRank: (level: string) =>
    apiClient.get<MyRankResponse>('/exams/my-rank', { params: { level } }).then((r) => r.data),
}

import { apiClient } from './client'
import type { LearningProfileRequest, LearningProfileResponse } from './types'

export const profileApi = {
  getLearning: () => apiClient.get<LearningProfileResponse>('/profile/learning').then((r) => r.data),

  updateLearning: (payload: LearningProfileRequest) =>
    apiClient.put<LearningProfileResponse>('/profile/learning', payload).then((r) => r.data),
}

import { apiClient } from './client'
import type { FsrsParametersResponse, LearningProfileRequest, LearningProfileResponse } from './types'

export const profileApi = {
  getLearning: () => apiClient.get<LearningProfileResponse>('/profile/learning').then((r) => r.data),

  updateLearning: (payload: LearningProfileRequest) =>
    apiClient.put<LearningProfileResponse>('/profile/learning', payload).then((r) => r.data),

  /** Tối ưu FSRS theo lịch sử ôn ngay bây giờ (app cũng tự chạy mỗi tuần). */
  optimizeFsrs: () =>
    apiClient.post<FsrsParametersResponse>('/profile/learning/fsrs/optimize').then((r) => r.data),
}

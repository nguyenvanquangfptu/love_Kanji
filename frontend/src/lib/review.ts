import { useMutation, useQueryClient } from '@tanstack/react-query'
import { srsApi } from '@/api/srs'
import type { AddSrsCardsResponse } from '@/api/types'

/** Đưa từ vào lịch ôn SRS, rồi làm mới mọi query ['srs', ...] (thẻ hôm nay, thống kê, trạng thái từng bài). */
export function useAddToReview() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: srsApi.addCards,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['srs'] }),
  })
}

export function describeAddResult({ added, alreadyInReview }: AddSrsCardsResponse) {
  if (added === 0) return 'Các từ này đều đã có trong Ôn tập.'
  return `Đã thêm ${added} từ vào Ôn tập${alreadyInReview > 0 ? ` (${alreadyInReview} từ đã có sẵn)` : ''}.`
}

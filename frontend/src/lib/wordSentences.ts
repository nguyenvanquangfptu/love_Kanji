import { queryOptions } from '@tanstack/react-query'
import { kanjiApi } from '@/api/kanji'

/** Câu của một từ: dùng chung cho thẻ đang xem và để tải trước thẻ kế tiếp. Câu của từ ít khi đổi nên giữ lâu. */
export function wordSentencesQuery(kanjiId: number) {
  return queryOptions({
    queryKey: ['kanji', kanjiId, 'sentences'],
    queryFn: () => kanjiApi.getSentences(kanjiId),
    staleTime: 30 * 60 * 1000,
  })
}

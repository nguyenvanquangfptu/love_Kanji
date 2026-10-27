import { useQuery } from '@tanstack/react-query'
import { characterApi } from '@/api/characters'

/** Các chữ Hán của các từ, hỏi một lần cho cả danh sách; chữ thành phần gần như không đổi nên giữ lâu trong cache. */
export function useCharacterParts(wordIds: number[]) {
  return useQuery({
    queryKey: ['characters', 'parts', wordIds],
    queryFn: () => characterApi.parts(wordIds),
    enabled: wordIds.length > 0,
    staleTime: 30 * 60_000,
  })
}
